#!/usr/bin/env bash
# NICE external 테스트를 stage 서버의 NAT IP로 실행한다.
# 로컬 PC IP는 NICE에 미등록이므로 SSM 경유 SOCKS 터널로 트래픽을 stage EC2로 보낸다.
set -euo pipefail

SSH_HOST="${SSH_HOST:-twinly-stage-api-a}"
PORT="${SOCKS_PORT:-1080}"
TESTS="${1:-*NiceAuthExternalTest*}"

cd "$(dirname "$0")/../backend"

ssh -N -D "127.0.0.1:${PORT}" "${SSH_HOST}" &
SSH_PID=$!
trap 'kill "${SSH_PID}" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  if nc -z 127.0.0.1 "${PORT}" 2>/dev/null; then
    break
  fi
  sleep 1
done

if ! nc -z 127.0.0.1 "${PORT}" 2>/dev/null; then
  echo "SOCKS 터널이 열리지 않았습니다. ssh ${SSH_HOST} 접속을 먼저 확인하세요." >&2
  exit 1
fi

EXTERNAL_TEST_SOCKS_PROXY="127.0.0.1:${PORT}" ./gradlew externalTest --tests "${TESTS}"
