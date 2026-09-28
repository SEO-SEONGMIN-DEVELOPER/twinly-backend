#!/usr/bin/env bash
# 캠페인 디렉터리의 수신자에게 SES로 한 통씩 보낸다. 성공한 주소는 sent.log에 남겨 재실행 시 건너뛴다.
# 디렉터리 구성: recipients.txt(한 줄에 주소 하나), subject.txt, body.html, body.txt — 디렉터리 이름이 campaign 태그가 된다.
set -euo pipefail

DIR="${1:?캠페인 디렉터리를 지정하세요}"
CAMPAIGN="$(basename "${DIR}")"
FROM="${FROM:-noreply@trytwinly.com}"
CONFIG_SET="${CONFIG_SET:-twinly-prod-bulk-mail}"
export AWS_PROFILE="${AWS_PROFILE:-admin}"
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"

if [[ ! "${CAMPAIGN}" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "디렉터리 이름은 영문·숫자·_·- 만 쓸 수 있습니다 (SES 태그 값 제약)." >&2
  exit 1
fi
for f in recipients.txt subject.txt body.html body.txt; do
  if [[ ! -s "${DIR}/${f}" ]]; then
    echo "${DIR}/${f} 가 없거나 비어 있습니다." >&2
    exit 1
  fi
done

CONTENT="$(mktemp)"
trap 'rm -f "${CONTENT}"' EXIT
jq -n \
  --rawfile subject "${DIR}/subject.txt" \
  --rawfile html "${DIR}/body.html" \
  --rawfile text "${DIR}/body.txt" \
  '{Simple: {
     Subject: {Data: ($subject | sub("\\s+$"; "")), Charset: "UTF-8"},
     Body: {Html: {Data: $html, Charset: "UTF-8"}, Text: {Data: $text, Charset: "UTF-8"}}
   }}' > "${CONTENT}"

touch "${DIR}/sent.log"
: > "${DIR}/failed.log"

PENDING="$(tr -d '\r' < "${DIR}/recipients.txt" \
  | tr '[:upper:]' '[:lower:]' \
  | awk '{$1=$1} NF' \
  | sort -u \
  | grep -vxF -f <(cut -f1 "${DIR}/sent.log") || true)"
TOTAL="$(grep -c . <<< "${PENDING}" || true)"
echo "보낼 대상 ${TOTAL}명 (이미 보낸 $(wc -l < "${DIR}/sent.log" | tr -d ' ')명 제외), campaign=${CAMPAIGN}"

n=0
ok=0
while IFS= read -r to; do
  [[ -n "${to}" ]] || continue
  n=$((n + 1))
  if id="$(aws sesv2 send-email \
      --from-email-address "${FROM}" \
      --destination "$(jq -cn --arg to "${to}" '{ToAddresses: [$to]}')" \
      --content "file://${CONTENT}" \
      --configuration-set-name "${CONFIG_SET}" \
      --email-tags "Name=campaign,Value=${CAMPAIGN}" \
      --query MessageId --output text < /dev/null 2>> "${DIR}/failed.log")"; then
    printf '%s\t%s\n' "${to}" "${id}" >> "${DIR}/sent.log"
    ok=$((ok + 1))
    echo "[${n}/${TOTAL}] ${to}"
  else
    printf 'FAILED\t%s\n' "${to}" >> "${DIR}/failed.log"
    echo "[${n}/${TOTAL}] ${to} 실패" >&2
  fi
done <<< "${PENDING}"

echo "완료: 성공 ${ok}, 실패 $((n - ok)). 실패한 주소는 같은 명령을 다시 실행하면 재시도합니다 (${DIR}/failed.log)."
