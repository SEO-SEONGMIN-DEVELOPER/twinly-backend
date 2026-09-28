#!/usr/bin/env bash
# ses-bulk-send.sh 로 보낸 캠페인의 수신자별 전달·반송·스팸신고·열람·클릭 횟수를 CloudWatch Logs Insights 로 조회한다.
# SES 가 봇으로 추정한(isBotEvent=Likely) 열람·클릭은 세지 않는다.
set -euo pipefail

CAMPAIGN="${1:?campaign 이름(발송한 디렉터리 이름)을 지정하세요}"
DAYS="${2:-14}"
LOG_GROUP="${LOG_GROUP:-/twinly/prod/ses-bulk-mail}"
export AWS_PROFILE="${AWS_PROFILE:-admin}"
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"

QUERY_ID="$(aws logs start-query \
  --log-group-name "${LOG_GROUP}" \
  --start-time "$(( $(date +%s) - DAYS * 86400 ))" \
  --end-time "$(date +%s)" \
  --query-string "filter \`detail.mail.tags.campaign.0\` = \"${CAMPAIGN}\"
    | fields \`detail.mail.destination.0\` as to, \`detail.eventType\` as type,
             coalesce(\`detail.open.isBotEvent\`, \`detail.click.isBotEvent\`, \"No\") as bot
    | stats count(*) as n by to, type, bot" \
  --query queryId --output text)"

while :; do
  RESULT="$(aws logs get-query-results --query-id "${QUERY_ID}")"
  STATUS="$(jq -r .status <<< "${RESULT}")"
  case "${STATUS}" in
    Complete) break ;;
    Scheduled | Running) sleep 1 ;;
    *) echo "쿼리 실패: ${STATUS}" >&2; exit 1 ;;
  esac
done

jq -r '
  [.results[] | map({(.field): .value}) | add]
  | map(select(.bot != "Likely"))
  | group_by(.to)
  | map(reduce .[] as $r ({to: .[0].to}; .[$r.type] = ($r.n | tonumber)))
  | (["TO", "Delivery", "Bounce", "Complaint", "Open", "Click"] | @tsv),
    (.[] | [.to, (.Delivery // 0), (.Bounce // 0), (.Complaint // 0), (.Open // 0), (.Click // 0)] | @tsv)
' <<< "${RESULT}" | column -t -s $'\t'
