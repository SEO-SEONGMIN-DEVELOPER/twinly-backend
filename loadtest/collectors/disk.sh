#!/bin/bash
# 대상 서버의 루트 볼륨 디스크 I/O 를 CSV 로 흘려보낸다.
#
# mpstat 의 %iowait 은 "CPU 가 디스크를 기다린 비율"이라 CPU 관점이다.
# CPU 가 다른 일로 바쁘면 디스크가 포화여도 낮게 나올 수 있어, 장치 관점의
# %util 과 await 를 따로 본다. snap 이 만드는 loop 장치는 잡음이라 제외한다.
set -u
INTERVAL="${1:-1}"
DEV="${2:-nvme0n1}"

echo "ts,r_s,rkb_s,r_await_ms,w_s,wkb_s,w_await_ms,aqu_sz,util_pct"

iostat -x -d "$INTERVAL" | awk -v dev="$DEV" '
  $1 == dev {
    "date +%s.%3N" | getline now
    close("date +%s.%3N")
    printf "%s,%s,%s,%s,%s,%s,%s,%s,%s\n", now, $2, $3, $6, $8, $9, $12, $21, $22
    fflush()
  }
'
