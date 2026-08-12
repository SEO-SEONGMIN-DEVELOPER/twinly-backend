#!/bin/bash
# 대상 서버의 컨테이너·호스트 메모리를 주기적으로 CSV 로 흘려보낸다.
#
# JVM 힙만 봐서는 부족하다. 컨테이너는 mem_limit 을 넘으면 도커가 죽이는데,
# 힙 밖 메모리(메타스페이스·스레드 스택·네이티브 버퍼)는 힙 지표에 안 잡힌다.
# 부하로 스레드가 수백 개까지 늘면 이쪽이 먼저 찬다.
set -u
INTERVAL="${1:-5}"

echo "ts,container_mb,container_limit_mb,container_pct,host_used_mb,host_free_mb,host_avail_mb,swap_used_mb"

while true; do
  now=$(date +%s.%3N)

  stats=$(docker stats --no-stream --format '{{.MemUsage}}\t{{.MemPerc}}' twinly-app-1 2>/dev/null)
  used=$(echo "$stats" | awk -F'/' '{print $1}' | tr -d ' ')
  limit=$(echo "$stats" | awk -F'/' '{print $2}' | awk '{print $1}' | tr -d ' ')
  pct=$(echo "$stats" | awk '{print $NF}' | tr -d '%')

  # MiB/GiB 표기를 MiB 숫자로 통일
  to_mb() {
    case "$1" in
      *GiB) echo "$1" | sed 's/GiB//' | awk '{printf "%.0f", $1*1024}' ;;
      *MiB) echo "$1" | sed 's/MiB//' | awk '{printf "%.0f", $1}' ;;
      *KiB) echo "$1" | sed 's/KiB//' | awk '{printf "%.1f", $1/1024}' ;;
      *)    echo "" ;;
    esac
  }

  read -r h_used h_free h_avail <<<"$(free -m | awk '/^Mem:/ {print $3, $4, $7}')"
  swap=$(free -m | awk '/^Swap:/ {print $3}')

  echo "$now,$(to_mb "$used"),$(to_mb "$limit"),${pct:-},${h_used:-},${h_free:-},${h_avail:-},${swap:-}"
  sleep "$INTERVAL"
done
