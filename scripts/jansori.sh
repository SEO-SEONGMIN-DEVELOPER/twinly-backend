#!/usr/bin/env bash
set -euo pipefail

PROFILE=infra
REGION=ap-northeast-2
PARAMETER_GROUP=twinly-prod-mysql84
DB_INSTANCE=twinly-prod-api-rds
DB_HOST=twinly-prod-api-rds.c3ue242e8vwz.ap-northeast-2.rds.amazonaws.com
DB_NAME=twinly_api
DB_USER=jansori
JUMP_INSTANCE=i-0f9ae4a42137c72f9
LOCAL_PORT=13306
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

aws_rds() {
  aws rds "$@" --profile "$PROFILE" --region "$REGION"
}

wait_in_sync() {
  for _ in $(seq 1 60); do
    status=$(aws_rds describe-db-instances --db-instance-identifier "$DB_INSTANCE" \
      --query 'DBInstances[0].DBParameterGroups[0].ParameterApplyStatus' --output text)
    if [ "$status" = "in-sync" ]; then
      return 0
    fi
    sleep 5
  done
  echo "파라미터 적용 대기 시간 초과: $status" >&2
  return 1
}

show_status() {
  aws_rds describe-db-parameters --db-parameter-group-name "$PARAMETER_GROUP" \
    --query "Parameters[?ParameterName=='general_log'||ParameterName=='log_output'].[ParameterName,ParameterValue,Source]" \
    --output table
}

case "${1:-}" in
  on)
    aws_rds modify-db-parameter-group --db-parameter-group-name "$PARAMETER_GROUP" --parameters \
      "ParameterName=log_output,ParameterValue=TABLE,ApplyMethod=immediate" \
      "ParameterName=general_log,ParameterValue=1,ApplyMethod=immediate" >/dev/null
    wait_in_sync
    show_status
    ;;
  off)
    aws_rds reset-db-parameter-group --db-parameter-group-name "$PARAMETER_GROUP" --parameters \
      "ParameterName=general_log,ApplyMethod=immediate" \
      "ParameterName=log_output,ApplyMethod=immediate" >/dev/null
    wait_in_sync
    show_status
    echo "mysql.general_log 에 남은 기록은 관리자 계정으로 CALL mysql.rds_rotate_general_log; 를 두 번 실행해 비웁니다."
    ;;
  status)
    show_status
    ;;
  tunnel)
    aws ssm start-session --profile "$PROFILE" --region "$REGION" --target "$JUMP_INSTANCE" \
      --document-name AWS-StartPortForwardingSessionToRemoteHost \
      --parameters "host=$DB_HOST,portNumber=3306,localPortNumber=$LOCAL_PORT"
    ;;
  up)
    if [ -f "$ROOT/.env.jansori" ]; then
      password=$(sed -n 's/^JANSORI_DB_PASSWORD=//p' "$ROOT/.env.jansori")
    else
      read -rsp "$DB_USER 비밀번호: " password
      echo
    fi
    encoded=$(printf '%s' "$password" | python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read(), safe=""))')
    unset password
    JANSORI_DATABASE_URL="mysql://$DB_USER:$encoded@host.docker.internal:$LOCAL_PORT/$DB_NAME" \
      docker compose -f "$ROOT/docker-compose.jansori.yaml" run --rm --build --service-ports jansori
    ;;
  purge)
    MYSQL_PWD=$(aws ssm get-parameter --profile "$PROFILE" --region "$REGION" \
      --name /twinly/prod/db-master-password --with-decryption --query Parameter.Value --output text) \
      docker run --rm -e MYSQL_PWD --add-host=host.docker.internal:host-gateway mysql:8.4 \
      mysql -h host.docker.internal -P "$LOCAL_PORT" -u admin -e \
      "CALL mysql.rds_rotate_general_log; CALL mysql.rds_rotate_general_log; SELECT COUNT(*) AS general_log_rows FROM mysql.general_log; SELECT COUNT(*) AS backup_rows FROM mysql.general_log_backup;"
    ;;
  *)
    echo "사용법: $0 {on|off|status|tunnel|up|purge}" >&2
    exit 1
    ;;
esac
