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

# 같은 시나리오·모드 안에서 몇 번째 실행인지로 폴더를 나눈다.
# 실행 시각은 start-utc.txt 에 남으므로 폴더명에 중복해 넣지 않는다.
PREFIX="${SCENARIO%.js}-${MODE}"
# 첫 실행이면 매칭되는 폴더가 없어 ls 가 실패한다. pipefail 때문에 스크립트가
# 통째로 죽으므로 실패를 흡수한다.
LAST=$( { ls -d "results/$PREFIX-"[0-9]* 2>/dev/null || true; } | sed -E "s|.*/$PREFIX-([0-9]+)$|\1|" | sort -n | tail -1)
RUN=$(( ${LAST:-0} + 1 ))
OUT="results/$PREFIX-$RUN"
mkdir -p "$OUT"

echo "결과 폴더: $OUT (${PREFIX} ${RUN}회차)"

cleanup() {
  echo "수집기 정리 중..."
  [[ -n "${SCRAPE_PID:-}" ]] && kill "$SCRAPE_PID" 2>/dev/null || true
  [[ -n "${MPSTAT_PID:-}" ]] && kill "$MPSTAT_PID" 2>/dev/null || true
  [[ -n "${GENCPU_PID:-}" ]] && kill "$GENCPU_PID" 2>/dev/null || true
  [[ -n "${MEM_PID:-}" ]] && kill "$MEM_PID" 2>/dev/null || true
  [[ -n "${DISK_PID:-}" ]] && kill "$DISK_PID" 2>/dev/null || true
  ssh "$LOADGEN" 'pkill -f scrape.py' 2>/dev/null || true
  ssh "$TARGET" 'pkill -f "mpstat -P ALL"; pkill -f "loadtest/mem.sh"; pkill -f "loadtest/disk.sh"; pkill -f "iostat -x"' 2>/dev/null || true
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

# 4) 대상 컨테이너·호스트 메모리 — 힙 지표만으로는 mem_limit 초과를 못 본다
ssh "$TARGET" 'bash ~/loadtest/mem.sh 5' > "$OUT/target-mem.csv" 2>&1 &
MEM_PID=$!

# 5) 대상 디스크 I/O — %iowait 은 CPU 관점이라 장치 포화를 놓칠 수 있다
ssh "$TARGET" 'bash ~/loadtest/disk.sh 1' > "$OUT/target-disk.csv" 2>&1 &
DISK_PID=$!

sleep 3   # 수집기가 첫 샘플을 남길 시간

# 6) 쿼리 다이제스트 초기화 — 부하 구간만 정확히 잘라내기 위해
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

# 시계열 집계는 원시 파일이 있는 생성기에서 돌린다.
# 무릎이 어느 RPS 인지는 summary.json 으로는 알 수 없고 이 표에서만 나온다.
TAG="${SCENARIO%%-*}"
ssh "$LOADGEN" "python3 ~/loadtest/agg.py $TAG" > "$OUT/timeline.txt" 2>&1 || true

# 원시 파일은 200MB 대라 그대로 끌면 SSM 터널에서 끊긴다. 압축하면 30분의 1 이 된다.
ssh "$LOADGEN" 'gzip -6 -c /tmp/k6-raw.json > /tmp/k6-raw.json.gz' 2>/dev/null || true
scp -q "$LOADGEN:/tmp/k6-raw.json.gz" "$OUT/k6-raw.json.gz" 2>/dev/null || true

# 전송이 온전한지 확인한다. 잘린 파일을 온전한 것으로 착각하면 분석이 조용히 틀어진다.
if [[ -f "$OUT/k6-raw.json.gz" ]]; then
  if gzip -t "$OUT/k6-raw.json.gz" 2>/dev/null; then
    echo "  원시 시계열 회수 완료 ($(du -h "$OUT/k6-raw.json.gz" | cut -f1), 무결성 확인)"
  else
    echo "  경고: 원시 시계열이 전송 중 손상됨. 생성기 /tmp/k6-raw.json 이 원본"
    rm -f "$OUT/k6-raw.json.gz"
  fi
fi

echo
echo "=== 수집 결과 ==="
for f in "$OUT"/*; do
  printf '  %-24s %s\n' "$(basename "$f")" "$(wc -l < "$f" 2>/dev/null || echo -) 줄"
done
echo
echo "구간: $(cat "$OUT/start-utc.txt") ~ $(cat "$OUT/end-utc.txt")"
echo "RDS 지표: ./collect-rds.sh $OUT"
