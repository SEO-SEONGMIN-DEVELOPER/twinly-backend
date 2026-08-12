#!/bin/bash
# performance_schema 의 쿼리 다이제스트 통계를 초기화하거나 덤프한다.
# 대상 서버(api-c)에서 실행한다. Performance Insights 가 SCP 로 막혀 있어 이걸로 대신한다.
#
#   pfs.sh reset   부하 직전 — 통계를 비워 구간을 정확히 자른다
#   pfs.sh dump    부하 직후 — 부하 구간의 쿼리별 누적 통계
set -euo pipefail

set -a
eval "$(sudo grep -E '^(DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|DB_PASSWORD)=' /home/ubuntu/twinly/.env)"
set +a
export MYSQL_PWD="$DB_PASSWORD"

run() { mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" "$@" 2>/dev/null; }

case "${1:-}" in
  reset)
    run -e "TRUNCATE performance_schema.events_statements_summary_by_digest;"
    echo "digest 통계 초기화 완료"
    ;;

  dump)
    # TIMER_WAIT 단위는 피코초. 1e9 로 나누면 밀리초.
    run -t -e "
      SELECT
        COUNT_STAR                              AS calls,
        ROUND(SUM_TIMER_WAIT/1e9,   1)          AS total_ms,
        ROUND(AVG_TIMER_WAIT/1e9,   3)          AS avg_ms,
        ROUND(MAX_TIMER_WAIT/1e9,   3)          AS max_ms,
        SUM_ROWS_EXAMINED                       AS rows_examined,
        SUM_ROWS_SENT                           AS rows_sent,
        SUM_NO_INDEX_USED                       AS no_index,
        SUM_CREATED_TMP_DISK_TABLES             AS tmp_disk,
        LEFT(REPLACE(DIGEST_TEXT,'\n',' '), 110) AS query
      FROM performance_schema.events_statements_summary_by_digest
      WHERE DIGEST_TEXT IS NOT NULL
        AND DIGEST_TEXT NOT LIKE 'TRUNCATE%'
        AND DIGEST_TEXT NOT LIKE 'SELECT \`COUNT_STAR\`%'
      ORDER BY SUM_TIMER_WAIT DESC
      LIMIT 15;
    "
    ;;

  *)
    echo "사용법: pfs.sh {reset|dump}" >&2
    exit 1
    ;;
esac
