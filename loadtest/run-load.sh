#!/bin/bash
# 부하 실행 + 앱/호스트 지표 실시간 수집.
# 로컬에서 실행한다. 부하 생성기와 대상 서버 양쪽에 SSH 가 필요하다.
#
#   ./run-load.sh main-limit.js            본 측정
#   ./run-load.sh people-limit.js smoke    스모크런
set -euo pipefail

SCENARIO="${1:?사용법: run-load.sh <시나리오.js> [smoke]}"
MODE="${2:-full}"

LOADGEN=twinly-stage-loadgen
TARGET=twinly-stage-api-c
TARGET_IP=10.0.23.25

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="results/${SCENARIO%.js}-${MODE}-${STAMP}"
mkdir -p "$OUT"

echo "결과 폴더: $OUT"

cleanup() {
  echo "수집기 정리 중..."
  [[ -n "${SCRAPE_PID:-}" ]] && kill "$SCRAPE_PID" 2>/dev/null || true
  [[ -n "${MPSTAT_PID:-}" ]] && kill "$MPSTAT_PID" 2>/dev/null || true
  [[ -n "${GENCPU_PID:-}" ]] && kill "$GENCPU_PID" 2>/dev/null || true
  ssh "$LOADGEN" 'pkill -f scrape.py' 2>/dev/null || true
  ssh "$TARGET" 'pkill -f "mpstat -P ALL"' 2>/dev/null || true
}
trap cleanup EXIT

date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/start-utc.txt"

# 1) 앱 내부 지표 — 부하 생성기에서 대상 8081 을 5초마다
ssh "$LOADGEN" "python3 ~/loadtest/scrape.py http://$TARGET_IP:8081/actuator/prometheus 5" \
  > "$OUT/app-metrics.csv" 2>"$OUT/app-metrics.err" &
SCRAPE_PID=$!

# 2) 대상 호스트 CPU — 코어별 1초
ssh "$TARGET" 'mpstat -P ALL 1' > "$OUT/target-cpu.txt" 2>&1 &
MPSTAT_PID=$!

# 3) 생성기 자신의 CPU — 생성기가 병목이면 측정 전체가 무효
ssh "$LOADGEN" 'mpstat -P ALL 1' > "$OUT/loadgen-cpu.txt" 2>&1 &
GENCPU_PID=$!

sleep 3   # 수집기가 첫 샘플을 남길 시간

# 4) 쿼리 다이제스트 초기화 — 부하 구간만 정확히 잘라내기 위해
#    Performance Insights 가 SCP 로 막혀 있어 performance_schema 로 대신한다.
ssh "$TARGET" 'bash ~/loadtest/pfs.sh reset' | sed 's/^/  /'

echo "부하 시작: $SCENARIO (mode=$MODE)"
ENVFLAG=""
[[ "$MODE" == "smoke" ]] && ENVFLAG="--env MODE=smoke"

# k6 는 생성기에서 분리 실행한다. SSH 가 끊겨도 부하는 계속되고 결과가 남는다.
ssh "$LOADGEN" "cd ~/loadtest && rm -f /tmp/k6.log /tmp/k6.done && \
  nohup sh -c 'k6 run $ENVFLAG --summary-export=/tmp/summary.json --out json=/tmp/k6-raw.json $SCENARIO > /tmp/k6.log 2>&1; echo \$? > /tmp/k6.done' >/dev/null 2>&1 &"

echo "진행 중... (완료까지 대기)"
until ssh -o ConnectTimeout=30 "$LOADGEN" 'test -f /tmp/k6.done' 2>/dev/null; do
  sleep 20
  ssh -o ConnectTimeout=30 "$LOADGEN" 'tail -2 /tmp/k6.log 2>/dev/null | tr -d "\r" | tail -1' 2>/dev/null | sed 's/^/    /' || true
done
ssh "$LOADGEN" 'cat /tmp/k6.log' > "$OUT/k6.log" 2>&1
K6RC=$(ssh "$LOADGEN" 'cat /tmp/k6.done' 2>/dev/null || echo "?")
[[ "$K6RC" != "0" ]] && echo "(k6 종료 코드 $K6RC — 임계값 초과로 중단되었을 수 있음)"

date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/end-utc.txt"

# 부하 구간의 쿼리별 통계를 즉시 덤프한다. 이후 트래픽이 섞이기 전에 받아야 한다.
ssh "$TARGET" 'bash ~/loadtest/pfs.sh dump' > "$OUT/db-top-queries.txt" 2>&1 || true

sleep 3   # 마지막 샘플 확보
cleanup
trap - EXIT

scp -q "$LOADGEN:/tmp/summary.json" "$OUT/summary.json" 2>/dev/null || true
scp -q "$LOADGEN:/tmp/k6-raw.json"  "$OUT/k6-raw.json"  2>/dev/null || true

echo
echo "=== 수집 결과 ==="
for f in "$OUT"/*; do
  printf '  %-24s %s\n' "$(basename "$f")" "$(wc -l < "$f" 2>/dev/null || echo -) 줄"
done
echo
echo "구간: $(cat "$OUT/start-utc.txt") ~ $(cat "$OUT/end-utc.txt")"
echo "RDS 지표: ./collect-rds.sh $OUT"
