#!/usr/bin/env bash
# ses-bulk-send.sh 로 보낸 캠페인의 수신자별 전달·반송·스팸신고·열람·클릭 횟수와 클릭 상세를 CloudWatch Logs Insights 로 조회한다.
# SES 가 봇으로 추정한(isBotEvent=Likely) 열람·클릭은 세지 않는다. 요약은 사람 수이고, 클릭한 사람은 열람으로도 센다.
# EXCLUDE_FILE(한 줄에 주소 하나)에 적힌 주소는 표·요약·클릭 상세에서 모두 뺀다. 운영진 주소를 저장소에 남기지 않으려고 파일로 둔다.
set -euo pipefail

CAMPAIGN="${1:?campaign 이름(발송한 디렉터리 이름)을 지정하세요}"
DAYS="${2:-14}"
LOG_GROUP="${LOG_GROUP:-/twinly/prod/ses-bulk-mail}"
EXCLUDE_FILE="${EXCLUDE_FILE:-${HOME}/twinly-mail/report-exclude.txt}"
export AWS_PROFILE="${AWS_PROFILE:-admin}"
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"

EXCLUDE='[]'
if [[ -f "${EXCLUDE_FILE}" ]]; then
  EXCLUDE="$(jq -Rn '[inputs | ascii_downcase | gsub("^\\s+|\\s+$"; "") | select(length > 0)] | unique' < "${EXCLUDE_FILE}")"
fi

run_query() {
  local query_id result status
  query_id="$(aws logs start-query \
    --log-group-name "${LOG_GROUP}" \
    --start-time "$(( $(date +%s) - DAYS * 86400 ))" \
    --end-time "$(date +%s)" \
    --query-string "$1" \
    --query queryId --output text)"

  while :; do
    result="$(aws logs get-query-results --query-id "${query_id}")"
    status="$(jq -r .status <<< "${result}")"
    case "${status}" in
      Complete) printf '%s' "${result}"; return ;;
      Scheduled | Running) sleep 1 ;;
      *) echo "쿼리 실패: ${status}" >&2; exit 1 ;;
    esac
  done
}

COUNTS="$(run_query "filter \`detail.mail.tags.campaign.0\` = \"${CAMPAIGN}\"
  | fields \`detail.mail.destination.0\` as to, \`detail.eventType\` as type,
           coalesce(\`detail.open.isBotEvent\`, \`detail.click.isBotEvent\`, \"No\") as bot
  | stats count(*) as n by to, type, bot")"

CLICKS="$(run_query "filter \`detail.mail.tags.campaign.0\` = \"${CAMPAIGN}\" and \`detail.eventType\` = \"Click\"
  | fields \`detail.mail.destination.0\` as to, \`detail.click.timestamp\` as at, \`detail.click.link\` as link,
           coalesce(\`detail.click.isBotEvent\`, \"No\") as bot
  | sort at asc
  | limit 10000")"

TABLE="$(jq -r --argjson ex "${EXCLUDE}" '
  [.results[] | map({(.field): .value}) | add]
  | map(select(.bot != "Likely"))
  | map(select((.to | ascii_downcase) as $t | $ex | any(. == $t) | not))
  | group_by(.to)
  | map(reduce .[] as $r ({to: .[0].to}; .[$r.type] = ($r.n | tonumber)))
  | (["TO", "Delivery", "Bounce", "Complaint", "Open", "Click"] | @tsv),
    (.[] | [.to, (.Delivery // 0), (.Bounce // 0), (.Complaint // 0), (.Open // 0), (.Click // 0)] | @tsv)
' <<< "${COUNTS}")"

column -t -s $'\t' <<< "${TABLE}"

awk -F'\t' '
  NR > 1 {
    total++
    delivered += ($2 > 0)
    bounced += ($3 > 0)
    opened += ($5 > 0 || $6 > 0)
    clicked += ($6 > 0)
  }
  END {
    printf "\n수신자 %d명 | 전달 %d명 | 반송 %d명 | 열람 %d명 | 클릭 %d명\n", total, delivered, bounced, opened, clicked
  }
' <<< "${TABLE}"

EXCLUDED_COUNT="$(jq length <<< "${EXCLUDE}")"
if (( EXCLUDED_COUNT > 0 )); then
  echo "(제외한 주소 ${EXCLUDED_COUNT}개: ${EXCLUDE_FILE})"
fi

echo
echo "[클릭 상세]"
jq -r --argjson ex "${EXCLUDE}" '
  [.results[] | map({(.field): .value}) | add]
  | map(select(.bot != "Likely"))
  | map(select((.to | ascii_downcase) as $t | $ex | any(. == $t) | not))
  | (["TO", "At(KST)", "Link"] | @tsv),
    (.[] | [
      .to,
      (.at[0:19] + "Z" | fromdateiso8601 + 32400 | strftime("%m-%d %H:%M:%S")),
      (.link | sub("^https?://"; "") | sub("\\?.*$"; ""))
    ] | @tsv)
' <<< "${CLICKS}" | column -t -s $'\t'
