# API 변경 이력 (프론트엔드용)

백엔드 API의 **호환성이 깨지는 변경**과 **응답 계약 변경**을 최신순으로 기록합니다.
엔드포인트 전체 명세는 [Apidog / Swagger UI](api-specification.md), 에러 코드 의미는 [error-handling.md](error-handling.md)를 참조하세요.

각 항목의 표기 의미는 다음과 같습니다.

| 표기 | 의미 |
|---|---|
| **BREAKING** | 프론트 수정 없이는 해당 기능이 동작하지 않습니다. 배포 조율이 필요합니다. |
| **문서 보강** | 서버 동작은 이전과 같고, 문서에 빠져 있던 응답을 채웠습니다. 대응 여부는 선택입니다. |

---

## 2026-08-02 (2)

### 1. 읽음 표시가 실시간으로 전파됩니다 (`chat.read.advanced` 추가)

지금까지 상대가 내 메시지를 읽었는지 알 방법이 없었습니다. 두 가지가 추가됩니다. 기존 필드·이벤트는 그대로라 **호환성은 깨지지 않습니다.**

#### 1-1. `GET /api/v1/chat/rooms/{roomId}/messages` 응답에 `lastReadMessageId` 추가

`messages`와 `page` 사이에 들어갑니다.

```json
{
  "roomId": "1024",
  "messages": [ ... ],
  "lastReadMessageId": "88123",
  "page": { "nextCursor": null, "hasMore": false }
}
```

**`lastReadMessageId`는 "내"가 아니라 "상대"가 읽은 마지막 메시지 id입니다.** 이 id 이하의 내 메시지에 읽음 표시를 그리시면 됩니다. 상대가 아직 아무것도 읽지 않았으면 `null`입니다.

#### 1-2. 새 WebSocket 이벤트 `chat.read.advanced`

| 항목 | 값 |
|---|---|
| destination | `/user/queue/chat/rooms/{encodedRoomId}` (기존 방 큐, 추가 구독 불필요) |
| type | `chat.read.advanced` |
| envelope | 기존 `RealtimeEvent` 그대로 (`v:1`, `kind:'event'`, `eventId`, `occurredAt`) |

```json
{ "roomId": "1024", "lastReadMessageId": "88123" }
```

발행 조건은 두 가지입니다.

- **읽은 본인에게는 가지 않습니다.** 방의 다른 활성 참여자에게만 갑니다. 내 읽음 처리 결과는 기존대로 `chat.read.committed`로 받으세요.
- **읽음 위치가 실제로 전진했을 때만 발행됩니다.** 같은 위치로 다시 읽음 처리하거나 뒤로 가는 요청은 프레임을 만들지 않습니다.

#### 받은 값을 그대로 반영하시면 됩니다

**크기 비교로 걸러내실 필요가 없습니다.** 서버가 발행 순서 유지(`preservePublishOrder`)와 값의 단조 증가를 함께 보장합니다.

오히려 **직접 비교하지 마세요.** id는 문자열이라 `"1000" > "999"`가 `false`가 되어, 999→1000처럼 자릿수가 바뀌는 순간 새 값을 버리고 읽음 표시가 멈춥니다. 꼭 비교해야 한다면 `BigInt`를 쓰세요.

프레임을 놓쳤을 때의 복구 경로는 기존 원칙 그대로입니다 — 실시간 이벤트는 빠른 알림일 뿐이고, 진짜 출처는 REST입니다. 재연결 후 `GET /messages`의 `lastReadMessageId`로 맞추면 완전히 복구됩니다. 별도의 읽음 전용 조회 API를 만들지 않은 이유이기도 합니다.

#### 프론트 체크리스트

- [ ] 방 진입·재연결 시 `GET /messages`의 `lastReadMessageId`로 읽음 표시 초기화
- [ ] `chat.read.advanced` 수신 시 받은 값을 그대로 반영 (문자열 크기 비교 금지)
- [ ] 추가 구독 불필요 — 기존 `/user/queue/chat/rooms/{roomId}`에서 `type`으로 분기

자세한 처리 방법은 [websocket-client-guide.md](websocket-client-guide.md) 7-A장에 있습니다.

---

## 2026-08-02

### 1. **BREAKING** — 학과 입력이 기본 정보에서 빠지고, 학교 선택 → 이메일 인증 → 학과 선택 흐름으로 바뀝니다

온보딩 순서가 바뀝니다. 학과는 더 이상 기본 정보 단계에서 받지 않고, **이메일 인증을 마친 뒤** 별도 단계에서 받습니다.

```
학교 선택 → 이메일 입력 → 코드 입력 → 학과 선택
```

학교 선택 단계를 둔 이유는, 가입 가능한 학교를 보여주고 **선택한 학교의 이메일 도메인을 앱이 자동 입력·고정**하기 위해서입니다. 유저가 도메인을 직접 입력하지 않습니다.

#### 1-1. `PUT /api/v1/onboarding/basic-info`에서 `affiliation`이 제거됩니다

| 변경 전 | 변경 후 |
|---|---|
| `familyName`, `givenName`, `gender`, `affiliation`, `affiliationNumber`, `birthDate` | `familyName`, `givenName`, `gender`, `affiliationNumber`, `birthDate` |

`affiliation`을 계속 보내도 무시됩니다(400은 아닙니다). 다만 학과는 아래 `POST /api/v1/onboarding/affiliation`으로 보내지 않으면 저장되지 않고, 회원가입 시 `422 PROFILE_NOT_COMPLETED`가 납니다.

#### 1-2. `GET /api/v1/onboarding/schools` (신규) — 가입 가능한 학교 목록

인증 헤더가 필요 없습니다. 학교 이름순으로 내려옵니다.

```json
{
  "schools": [
    { "schoolName": "니두스대학교", "domain": "nidus.ac.kr" }
  ]
}
```

목록에 없는 학교는 가입할 수 없으므로, 자유 입력 fallback을 두지 마세요. 로딩 실패 시에는 재시도 UI로 처리합니다.

#### 1-3. `POST /api/v1/auth/onboarding/email/send`에 서버 측 도메인 검증이 추가됩니다

이메일 도메인이 `schools`에 없으면 인증번호를 보내지 않고 **422 `EMAIL_DOMAIN_NOT_SUPPORTED`** 를 반환합니다. 지금까지는 앱 UI로만 막혀 있었습니다.

> 개발 빌드의 "풀 이메일 직접 입력" 우회 경로도 이 검증에 걸립니다. 테스트용 도메인이 필요하면 `schools`에 추가해야 합니다.

#### 1-4. `GET /api/v1/onboarding/affiliations` (신규) — 인증한 학교의 학과 목록

익명 세션 토큰이 필요합니다. **요청 파라미터는 없습니다.** 서버가 해당 세션에서 인증 완료된 이메일의 도메인으로 학교를 판별해 내려줍니다.

```json
{
  "affiliations": ["경영학과", "컴퓨터공학과"]
}
```

이메일 인증을 마치지 않은 상태로 호출하면 `422 EMAIL_VERIFICATION_NOT_COMPLETED`가 납니다.

#### 1-5. `POST /api/v1/onboarding/affiliation` (신규) — 학과 저장

```
POST /api/v1/onboarding/affiliation
Authorization: Bearer {anonSessionToken}
Content-Type: application/json

{ "affiliation": "컴퓨터공학과" }
```

성공 시 `200 OK`, 바디 없음. **서버는 목록 일치 여부를 검증하지 않습니다.** 신설 학과 대응을 위해 검색 결과에 없는 값도 자유 입력으로 보낼 수 있습니다. 다만 빈 문자열·공백만은 `400`입니다.

#### 왜 바꿨는지

이메일 도메인 검증이 앱에만 있었습니다. 앱 UI를 거치지 않은 직접 호출로 아무 도메인이나 인증번호를 받을 수 있었고, 그대로 가입까지 이어졌습니다. 학교를 서버가 알고 있어야 막을 수 있어 `schools`를 서버로 옮겼습니다.

학과를 이메일 인증 뒤로 미룬 것은 그 결과입니다. 학교가 정해지기 전에는 보여줄 학과 목록을 특정할 수 없습니다. 반대로 인증이 끝나면 학교가 확정되므로, 학과 목록 조회에 **요청 파라미터가 필요 없습니다.** 클라이언트가 보낸 학교 식별자를 믿지 않아도 되고, 앱과 서버가 서로 다른 학교를 가리킬 여지도 없습니다.

학과는 목록 검증을 하지 않습니다. 신설·개편 학과가 서버 데이터보다 먼저 생기는 일이 잦은데, 이때 검증을 걸면 가입 자체가 막힙니다. 학교(도메인)는 가입 자격이라 엄격하게, 학과는 프로필 항목이라 느슨하게 두는 편이 사고 비용이 낮다고 봤습니다.

#### 프론트 체크리스트

- [ ] 기본 정보 단계에서 `affiliation` 필드 제거 (5단계 → 4단계)
- [ ] 이메일 입력 전 학교 선택 화면 추가 (`GET /schools`, 검색 지원, 자유 입력 없음)
- [ ] 선택한 학교의 도메인 자동 입력·고정 (수정하려면 뒤로 가서 학교 재선택)
- [ ] 이메일 발송 시 `422 EMAIL_DOMAIN_NOT_SUPPORTED` 처리
- [ ] 이메일 인증 후 학과 선택 화면 추가 (`GET /affiliations` → `POST /affiliation`)

---

## 2026-07-30

### 1. **BREAKING** — 약관 동의 철회 엔드포인트가 이동했습니다

| | 변경 전 | 변경 후 |
|---|---|---|
| 로그인 유저 | `DELETE /api/v1/me/consents` | `POST /api/v1/me/consents/revoke` |
| 온보딩(익명 세션) | `DELETE /api/v1/onboarding/consents` | `POST /api/v1/onboarding/consents/revoke` |

**메서드와 경로가 함께 바뀝니다.** 구 엔드포인트는 남겨두지 않았으므로, 이전 방식으로 호출하면 404 또는 405를 받습니다.

#### 바뀌지 않는 것

요청 바디, 응답, 에러 코드, 헤더는 **모두 그대로**입니다. 호출부의 메서드와 URL만 고치면 됩니다.

```
POST /api/v1/me/consents/revoke
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "grants": [
    { "policyId": "marketing", "version": "1" }
  ]
}
```

성공 시 `200 OK`, 바디 없음. 온보딩 쪽은 인증 헤더만 익명 세션 토큰으로 바뀌며, 바디 형식은 동일합니다.

> `version`은 **문자열**로 보냅니다(`"1"`). 서버는 문자열로 받아 정수로 변환합니다. 기존과 동일한 규약입니다.

#### 왜 바꿨는지

철회 대상 목록을 요청 바디로 받는데, 메서드가 `DELETE`였습니다. `DELETE`의 요청 바디는 RFC 9110에서 **의미가 정의되지 않아** 일부 HTTP 클라이언트, 리버스 프록시, CDN이 바디를 버려도 규격 위반이 아닙니다.

바디가 유실된 채 서버에 도착하면 `400 INVALID_REQUEST`가 나갑니다. 문제는 **이 400이 재현하기 어렵다**는 점입니다.

- 프론트 코드는 바디를 정상적으로 실어 보냈습니다
- 서버 코드도 정상입니다
- 로컬(프록시 없음)에서는 잘 됩니다
- 운영·스테이징(CDN·게이트웨이 경유)에서만 간헐적으로 실패합니다

바디 사용이 스펙상 정당한 `POST`로 옮기면 이 경로가 원천적으로 닫힙니다.
철회 대상이 `(policyId, version)` **쌍의 목록**이라 쿼리 파라미터로 옮기는 방식은 인덱스 대응 규약을 프론트에 전가하게 되어 채택하지 않았습니다.

#### 프론트 체크리스트

- [ ] API 클라이언트에서 두 호출의 메서드를 `delete` → `post`로, URL 끝에 `/revoke` 추가
- [ ] `DELETE`에 바디를 싣기 위해 넣어둔 우회 코드(예: axios `{ data: body }` 형태)가 있으면 일반 `post(url, body)`로 정리
- [ ] 동의 철회 화면에서 필수 약관 철회(403)와 없는 정책(404) 분기가 그대로 동작하는지 확인

### 2. **BREAKING** — 친밀도 시계열의 `from`/`to`가 날짜 형식으로 바뀝니다

`GET /api/v1/people/{userId}/intimacy-series`

| | 변경 전 | 변경 후 |
|---|---|---|
| `from` | `2026-07-01T00:00:00Z` (ISO 시각) | `2026-07-01` (ISO 날짜) |
| `to` | `2026-07-31T00:00:00Z` | `2026-07-31` |

시각 형식으로 보내면 `400 INVALID_REQUEST`를 받습니다.

```
GET /api/v1/people/42/intimacy-series?from=2026-07-01&to=2026-07-31&resolution=DAY&maxPoints=10
```

응답은 **바뀌지 않습니다.** `intimacySeries[].date`는 이전과 같이 `2026-07-01` 형식입니다.

#### 왜 바꿨는지

이 API의 시간축 단위는 **날짜**입니다. 응답의 `date`도, 서버가 조회하는 관계 기록의 날짜 컬럼도 날짜 단위인데, 요청만 시각을 받고 있었습니다.

받은 시각은 서버에서 곧바로 날짜로 깎여 버려졌고, 그 과정에서 **오프셋이 무시됐습니다.** 저장된 날짜는 KST 기준인데 시각을 자를 때는 보낸 오프셋을 그대로 썼기 때문입니다.

- `from=2026-07-01T00:00:00Z`(=KST 7/1 09:00) → 7월 1일. 의도대로 동작
- `from=2026-07-01T23:00:00Z`(=KST 7/2 08:00) → **7월 1일로 해석되어 하루가 밀립니다**

요청·응답·저장 형식을 모두 날짜로 맞춰 이 경로를 닫았습니다. 다른 날짜 기반 API(`GET /api/v1/activities/{date}`, `GET /api/v1/people/{userId}/events`의 `cursor`)는 이미 날짜 형식이라 규약도 함께 통일됩니다.

#### 프론트 체크리스트

- [ ] 친밀도 시계열 호출에서 `from`/`to`를 `YYYY-MM-DD`로 변경 (`toISOString()` 사용 중이면 날짜만 잘라 보내기)
- [ ] 기간 선택 UI가 로컬(KST) 기준 날짜를 그대로 보내는지 확인 — UTC로 변환하면 하루가 밀릴 수 있습니다

### 3. **문서 보강** — 읽음 처리 응답에 확정된 포인터가 담깁니다

`POST /api/v1/chat/rooms/{roomId}/read`

| | 변경 전 | 변경 후 |
|---|---|---|
| 응답 | `200 OK`, 바디 없음 | `200 OK` + 아래 JSON |

```json
{
  "roomId": "10",
  "lastMsgId": "77"
}
```

기존 클라이언트는 응답을 무시하면 되므로 **동작이 깨지지 않습니다.**

#### 왜 필요한지

읽음 포인터는 **전진만** 합니다. 뒤늦게 도착한 요청이 이전 `lastMsgId`를 보내면 서버는 그것을 반영하지 않고 기존 값을 유지합니다.

바디가 없던 동안에는 `200`만 보고 "내 요청이 반영됐다"고 판단할 수밖에 없어, 화면의 안읽음 수가 서버와 어긋날 수 있었습니다. 이제 응답의 `lastMsgId`가 **서버가 확정한 값**이므로 그 값으로 로컬 상태를 맞추면 됩니다.

WebSocket `/user/queue/chat/commands`의 읽음 committed 페이로드와 **필드명·형식이 같습니다**(`roomId`, `lastMsgId`, 둘 다 문자열). 두 경로를 한 벌의 코드로 처리할 수 있습니다.

#### 프론트 체크리스트

- [ ] 읽음 처리 후 로컬 포인터를 요청값이 아니라 응답의 `lastMsgId`로 갱신
