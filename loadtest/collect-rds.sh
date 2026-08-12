#!/bin/bash
# 부하 종료 후 RDS 지표를 조회한다.
# AWS 가 이미 저장해 둔 값을 구간 지정해 받아오므로, 부하 중에 호출할 필요가 없다.
#
#   ./collect-rds.sh results/main-limit-full-20260811-224500
set -euo pipefail

OUT="${1:?사용법: collect-rds.sh <결과폴더>}"
PROFILE="${AWS_PROFILE_OVERRIDE:-admin}"
DB=twinly-stage-api-rds
DBI=db-Q3SGBZH3ICODP674WGTHVGNYDY

START=$(cat "$OUT/start-utc.txt")
END=$(cat "$OUT/end-utc.txt")
echo "구간: $START ~ $END"

# 1) CloudWatch 표준 지표 (60초)
{
  echo "metric,timestamp,average,maximum"
  for m in CPUUtilization DatabaseConnections FreeableMemory FreeStorageSpace \
           ReadLatency WriteLatency ReadIOPS WriteIOPS ReadThroughput WriteThroughput; do
    aws cloudwatch get-metric-statistics --profile "$PROFILE" \
      --namespace AWS/RDS --metric-name "$m" \
      --dimensions Name=DBInstanceIdentifier,Value=$DB \
      --start-time "$START" --end-time "$END" --period 60 \
      --statistics Average Maximum \
      --query "sort_by(Datapoints,&Timestamp)[].['$m',Timestamp,Average,Maximum]" \
      --output text 2>/dev/null | tr '\t' ','
  done
} > "$OUT/rds-cloudwatch.csv"

# 2) 쿼리별 부하는 Performance Insights 대신 performance_schema 로 본다.
#    pi:GetResourceMetrics 가 조직 SCP 에서 명시적으로 거부되어 API 를 쓸 수 없다.
#    덤프는 run-load.sh 가 부하 직후에 받아 db-top-queries.txt 로 남긴다.

# 3) 요약 출력
echo
echo "=== RDS 요약 ==="
python3 - "$OUT" <<'PY'
import csv, json, pathlib, sys
out = pathlib.Path(sys.argv[1])

rows = {}
with (out / "rds-cloudwatch.csv").open() as f:
    for r in csv.reader(f):
        if len(r) != 4 or r[0] == "metric":
            continue
        rows.setdefault(r[0], []).append((float(r[2]), float(r[3])))

for m, vals in rows.items():
    avg = sum(v[0] for v in vals) / len(vals)
    mx = max(v[1] for v in vals)
    unit = "%" if "Utilization" in m else ""
    if "Memory" in m or "Storage" in m or "Throughput" in m:
        avg, mx, unit = avg / 1024**3, mx / 1024**3, "GiB"
    elif "Latency" in m:
        avg, mx, unit = avg * 1000, mx * 1000, "ms"
    print(f"  {m:22} 평균 {avg:>10.2f}{unit:<4} 최대 {mx:>10.2f}{unit}")

q = out / "db-top-queries.txt"
if q.exists() and q.stat().st_size:
    print("\n=== 부하 구간 쿼리별 통계 (performance_schema) ===")
    print(q.read_text().rstrip())
else:
    print("\n  (db-top-queries.txt 없음 — run-load.sh 로 실행해야 수집된다)")
PY
