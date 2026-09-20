#!/bin/bash
# 결제 이후 시뮬레이션 부하 재현. API 서버 호스트에서 root 로 실행한다 (SSM send-command).
# 결제를 대신해 AI 테스트 시드 유저(id 순 21번째부터 500명)에게 권한·약관 동의·pool 을 부여하고 선생성을 요청한다.
#
#   INTERVAL=180 COUNT=480 bash purchase-loop.sh
#
# 권한의 synced_at 을 2099 로 넣는 이유: persona 조회 시 RevenueCat 동기화가 구매 기록 없는
# 권한을 지우는데, 저장된 synced_at 이 더 최신이면 교체를 건너뛴다.
# 앱 재배포 시 시더가 AI 시드 유저 권한을 회수하므로, 실행 중에는 배포하지 않는다.
set -uo pipefail

INTERVAL="${INTERVAL:-180}"
COUNT="${COUNT:-480}"
SHOWCASE_COUNT=20
AI_TEST_COUNT=500
PRELOAD_DAYS=2
PRELOAD_ATTEMPTS=3
PRELOAD_RETRY_DELAY=2

env_of() { docker inspect twinly-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep "^$1=" | cut -d= -f2-; }

export MYSQL_PWD="$(env_of DB_PASSWORD)"
DB_HOST="$(env_of DB_HOST)"
DB_USER="$(env_of DB_USERNAME)"
DB_NAME="$(env_of DB_NAME)"
AI_URL="$(env_of AI_SERVER_BASE_URL)"

if [ -z "$DB_HOST" ] || [ -z "$AI_URL" ]; then
  echo "앱 컨테이너에서 DB·AI 서버 설정을 읽지 못했습니다." >&2
  exit 1
fi

sql() { mysql -h "$DB_HOST" -u "$DB_USER" "$DB_NAME" -N -e "$1"; }
ts() { TZ=Asia/Seoul date '+%F %T'; }

next_user() {
  sql "SELECT u.id FROM users u
       WHERE u.deleted_at IS NULL
         AND u.id IN (SELECT id FROM (SELECT id FROM users ORDER BY id LIMIT $SHOWCASE_COUNT, $AI_TEST_COUNT) ai_test)
         AND NOT EXISTS (SELECT 1 FROM user_entitlements e WHERE e.user_id = u.id AND e.entitlement = 'simulation_access')
       ORDER BY u.id LIMIT 1"
}

grant() {
  local uid="$1"
  sql "START TRANSACTION;
SELECT assigned_count INTO @assigned FROM pool_counter WHERE id = 1 FOR UPDATE;
INSERT INTO user_entitlements (user_id, entitlement, expires_at, synced_at)
VALUES ($uid, 'simulation_access', NULL, '2099-12-31 00:00:00');
INSERT INTO agreements (user_id, policy_id, agreed_at)
SELECT $uid, p.id, UTC_TIMESTAMP(6)
FROM policies p
JOIN policy_names pn ON pn.id = p.policy_name_id
WHERE pn.kind = 'PARALLEL_ENTRY' AND pn.requires_agreement = 1 AND pn.is_deprecated = 0
  AND p.is_required = 1
  AND p.id = (SELECT p2.id FROM policies p2
              WHERE p2.policy_name_id = pn.id AND p2.effective_at IS NOT NULL AND p2.effective_at <= UTC_TIMESTAMP(6)
              ORDER BY p2.effective_at DESC, p2.id DESC LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM agreements a WHERE a.user_id = $uid AND a.policy_id = p.id AND a.revoked_at IS NULL);
UPDATE users SET pool_number = @assigned DIV 50 + 1 WHERE id = $uid AND pool_number IS NULL;
SET @pooled = ROW_COUNT();
UPDATE pool_counter SET assigned_count = assigned_count + @pooled WHERE id = 1;
COMMIT;"
}

preload() {
  local uid="$1" granted_at dates body code attempt
  granted_at="$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S')"
  dates="$(for d in $(seq 0 $((PRELOAD_DAYS - 1))); do printf '"%s",' "$(TZ=Asia/Seoul date -d "+$d day" +%F)"; done)"
  body="{\"userId\":\"$uid\",\"grantedAt\":\"$granted_at\",\"dates\":[${dates%,}]}"

  for attempt in $(seq 1 "$PRELOAD_ATTEMPTS"); do
    code="$(curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST "$AI_URL/internal/v1/simulations/preload" \
      -H 'Content-Type: application/json' -d "$body")"
    if [[ "$code" == 2* ]]; then
      echo "preload=$code attempt=$attempt"
      return 0
    fi
    [ "$attempt" -lt "$PRELOAD_ATTEMPTS" ] && sleep "$PRELOAD_RETRY_DELAY"
  done

  echo "preload=FAILED($code) attempt=$PRELOAD_ATTEMPTS"
  return 1
}

START=$(date +%s)
echo "$(ts) 시작 interval=${INTERVAL}s count=$COUNT ai=$AI_URL"

for i in $(seq 0 $((COUNT - 1))); do
  target=$((START + i * INTERVAL))
  now=$(date +%s)
  [ "$now" -lt "$target" ] && sleep $((target - now))

  if ! uid="$(next_user 2>/dev/null)"; then
    echo "$(ts) #$((i + 1)) select=FAILED (DB 조회 실패, 다음 회차에 재시도)"
    continue
  fi
  if [ -z "$uid" ]; then
    echo "$(ts) #$((i + 1)) 부여할 유저가 없어 종료합니다."
    break
  fi

  if ! grant "$uid" >/dev/null 2>&1; then
    echo "$(ts) #$((i + 1)) user=$uid grant=FAILED (롤백, 다음 회차에 다시 선택됨)"
    continue
  fi

  echo "$(ts) #$((i + 1)) user=$uid grant=ok $(preload "$uid")"
done

echo "$(ts) 종료"
