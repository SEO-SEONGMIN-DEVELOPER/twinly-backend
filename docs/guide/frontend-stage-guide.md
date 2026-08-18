# Stage 서버 테스트 가이드 (FE)

## 1. 접속 정보

| 항목 | 값 |
| --- | --- |
| Base URL | `https://stage-api.trytwinly.com` |
| WebSocket | `wss://stage-api.trytwinly.com/ws/v1/?ticket={ticket}` |
| REST 명세 | `GET /docs/openapi` (UI: `/docs/swagger-ui.html`) |
| WebSocket 명세 | `GET /docs/asyncapi` |
| 에러 명세 | `GET /docs/openapi-error-specifications` |

## 2. 공통 규칙

- 인증 헤더는 익명 세션 토큰 / 액세스 토큰 모두 `Authorization: Bearer {token}` 입니다.
- 요청·응답의 모든 숫자 id(`userId`, `roomId`, `partnerId` 등)는 **JSON 문자열**로 주고받습니다.
- 에러 응답 포맷과 코드 목록은 `/docs/openapi-error-specifications` 를 그대로 따릅니다.

## 3. 인증 화이트리스트

아래 조건에 해당하면 **실제 SMS/이메일이 발송되지 않고** 인증코드가 고정됩니다. 회원가입·로그인 모두 동일하게 적용됩니다.

| 구분 | 조건 | 예시 |
| --- | --- | --- |
| 휴대폰 | `0100000` 으로 시작 | `01000001234` |
| 이메일 | `test` 로 시작 | `test01@trytwinly.com` |
| 인증코드 | 항상 `000000` | |

화이트리스트 밖의 번호/이메일은 실제 Solapi·SES 로 발송되고 코드도 랜덤 6자리이므로, 테스트에는 사용하지 마세요.

⚠️ **실제 발송은 과금됩니다.** stage 라고 해서 무료가 아니며, 운영과 동일한 계정·잔액을 씁니다.

- SMS: 발송 건당 약 18원이 Solapi 잔액에서 차감됩니다. 잔액이 바닥나면 **운영 인증 문자까지 함께 멈춥니다.**
- 이메일: 건당 비용은 미미하지만 발송 한도(일 5만 건)를 공유합니다.
- 발송된 문자·메일은 **회수할 수 없습니다.** 번호를 잘못 넣으면 모르는 사람에게 도달합니다.

반복 테스트는 반드시 화이트리스트 값으로 하세요.

## 4. 테스트 계정 만들기

1. `POST /api/v1/anon/start` → 익명 세션 토큰 발급
2. 이후 온보딩 API 는 익명 세션 토큰을 `Bearer` 로 실어 호출
   - 회원가입 필수값: `nickname`, `familyName`, `givenName`, `gender`, `organization`, `affiliation`, `affiliationNumber`, `birthDate`
   - 하나라도 비면 `PROFILE_NOT_COMPLETED`
   - `organization`, `affiliation` 은 미리 시드된 값을 써야 합니다 (8절 참고)
3. `POST /api/v1/auth/onboarding/sms/send` → `smsVerificationToken` 수신 → `POST /api/v1/auth/onboarding/sms/verify` (`code: "000000"`)
4. 이메일도 동일하게 `send` → `verify`
5. `POST /api/v1/auth/signup` → `accessToken`, `refreshToken` 발급

재로그인: `POST /api/v1/auth/sms/send` → `verify` 로 받은 `smsVerifiedToken` 을 `POST /api/v1/auth/login` 에 전달.

이미 가입된 번호/이메일로 다시 가입하면 `PHONE_ALREADY_REGISTERED` / `EMAIL_ALREADY_REGISTERED` 가 납니다. 새 화이트리스트 번호를 쓰세요.

## 5. AI 서버 없이 데이터 채우기

AI 서버가 하는 일은 **하루치 시뮬레이션 결과를 백엔드에 밀어넣는 것**뿐입니다. 그 API를 FE 가 직접 호출하면 mock 데이터로 전 화면을 테스트할 수 있습니다.

### 5-1. 사전 조건: 활성 시즌

`GET /api/v1/main`, 채팅방 개설은 활성 시즌이 있어야 동작합니다.

**stage 에서는 별도 조치가 필요 없습니다.** 활성 시즌이 하나도 없으면 앱이 뜰 때 자동으로 하나 생성됩니다 (8절 참고).

자동 생성이 걸리지 않는 상황(이미 비활성 시즌만 있는 등)에서 직접 만들어야 한다면 백엔드에 요청하세요.

```
POST /admin/season
X-Admin-Token: {백엔드에서 공유}
{ "startedAt": "2026-08-01T00:00:00Z", "endedAt": "2026-09-01T00:00:00Z" }
```

### 5-2. 시뮬레이션 mock 주입

```
POST /internal/v1/users/{userId}/simulations
```

- **인증 불필요** (`/internal/v1/**` 은 permitAll)
- 같은 `(userId, date)` 로 다시 호출하면 이전 데이터를 지우고 버전을 올립니다 → 몇 번이든 반복 주입 가능
- `partnerId` / `with` 에 넣는 userId 는 **실제로 가입된 사용자**여야 합니다. 테스트 계정을 2~3개 먼저 만들어 두세요.
- `rapport >= 70` 이면 채팅방이 자동 개설되고 매칭 알림이 쌓입니다. 지인 → 친구로 올라가면 친구 알림도 함께 생성됩니다.

요청 예시:

```json
{
  "userId": "1",
  "date": "2026-08-09",
  "scenes": [
    {
      "type": "action",
      "start": "2026-08-09T09:00:00",
      "end": "2026-08-09T09:30:00",
      "place": "강의동 앞",
      "with": ["2"],
      "narration": "아침 강의를 들으러 가는 길에 마주쳤다.",
      "mind": "오늘은 말을 걸어볼까"
    },
    {
      "type": "dialogue",
      "start": "2026-08-09T12:00:00",
      "end": "2026-08-09T12:40:00",
      "place": "학식당",
      "with": ["2"],
      "lines": [
        { "t": "narr", "text": "점심 줄에서 다시 만났다.", "occursAt": "2026-08-09T12:00:00" },
        { "t": "bubble", "userId": "1", "action": "웃으며", "text": "여기서 또 보네요?", "occursAt": "2026-08-09T12:05:00" },
        { "t": "bubble", "userId": "2", "action": "고개를 끄덕이며", "text": "그러게요, 같이 먹을래요?", "occursAt": "2026-08-09T12:06:00" }
      ]
    }
  ],
  "questions": [
    {
      "time": "2026-08-09T13:00:00",
      "qtype": "promise",
      "partnerId": ["2"],
      "text": "내일 같이 점심 먹을래?",
      "options": ["좋아", "다음에"]
    }
  ],
  "relationships": [
    {
      "partnerId": "2",
      "updateTime": "2026-08-09T13:10:00",
      "rapport": 72,
      "partnerModel": "조용하지만 대화가 시작되면 잘 받아주는 사람"
    }
  ]
}
```

- `type`: `action` | `dialogue`
- `lines[].t`: `narr` | `bubble`
- `lines[].action`: `bubble` 전용, **선택**. 생략하거나 `null` 로 보낼 수 있고 조회 시에도 `null` 로 내려옴
- `lines[].occursAt`: `YYYY-MM-DDTHH:mm:ss` 로컬 시각, **필수**. 빠뜨리면 요청 전체가 400
- `qtype`: `promise` | `persona`

`date` 를 제외한 모든 시각은 **날짜까지 포함한 로컬 시각**입니다. 타임존 오프셋(`+09:00`)을 붙이면 400 이니 붙이지 마세요.

`lines[].occursAt` 은 대사 한 줄이 나온 시각입니다. 조회 API 에서 절대시각(`2026-08-09T12:05:00+09:00`)으로 내려오므로, 주입할 때는 `start` ~ `end` 사이 값으로 넣어야 화면에서 자연스럽습니다. 서버는 범위나 순서를 검증하지 않습니다.

#### `date` 는 달력 날짜가 아니라 **하루 회차**입니다

`date` 는 "이 데이터가 어느 하루 묶음에 속하는가"를 나타내는 키일 뿐이고, 각 시각의 날짜와 달라도 됩니다. AI 서버는 하루를 **06:00 ~ 다음 날 04:00** 으로 만들기 때문에, `date: "2026-08-09"` 인 회차에 `start: "2026-08-10T01:30:00"` 같은 새벽 씬이 섞여 들어옵니다.

- `GET /api/v1/activities/2026-08-09` 는 그 새벽 씬까지 **함께** 내려줍니다. (8/10 조회에는 안 들어갑니다)
- 따라서 화면에 날짜를 표기할 때는 `date` 가 아니라 **각 씬의 `startsAt`** 을 쓰세요.

### 5-3. 이 주입으로 채워지는 화면

| API | 채워지는 것 |
| --- | --- |
| `GET /api/v1/activities/{date}` | 씬, 질문 |
| `GET /api/v1/people` | 관계가 생긴 상대 목록 |
| `GET /api/v1/people/{userId}/events`, `/events/{date}`, `/intimacy-series`, `/learned-facts` | 상대별 히스토리 |
| `GET /api/v1/chat/rooms` 및 채팅 API | `rapport >= 70` 로 열린 방 |
| `GET /api/v1/me/app-notifications/feeds`, `GET /api/v1/main` | 매칭·친구 알림, 미읽음 카운트 |

### 5-4. AI 서버 없이도 그대로 되는 것

- 온보딩 AI 대화(`POST /api/v1/onboarding/ai-chat/start`, `/messages`)는 백엔드가 Bedrock 을 직접 호출하므로 정상 동작합니다.
- `GET /internal/v1/users/{userId}/persona` 도 온보딩 데이터 기반이라 정상 동작합니다.

⚠️ AI 대화는 **호출할 때마다 실제 모델을 태우므로 토큰만큼 과금됩니다.** 화이트리스트 같은 우회 경로가 없어 스텁으로 대체되지 않습니다. 화면 확인이 목적이라면 대화를 끝까지 반복하기보다 필요한 턴만 호출하세요.

프로필 사진 업로드(S3)와 조회(CloudFront)도 마찬가지로 실제 저장·전송 비용이 발생합니다. 금액은 작지만 무료는 아닙니다.

## 6. WebSocket

1. `POST /api/v1/connection-tokens` (액세스 토큰 필요) → `ticket` 발급. 일회용이고 만료가 있으므로 연결 직전에 발급하세요.
2. `wss://stage-api.trytwinly.com/ws/v1/?ticket={ticket}` 로 STOMP 연결
3. 구독 `/user/queue/...`, 발행 `/app/...` — 목적지와 페이로드는 `/docs/asyncapi` 참고

## 7. 기타

- 프로필 사진: `presign` → 반환된 URL 로 S3 PUT → `commit` 순서. 조회 URL 은 CloudFront 서명 URL 이라 만료가 있으니 캐싱하지 마세요.
- 온보딩 진행 상태는 `GET /api/v1/me/status` 로 확인할 수 있습니다.

## 8. stage 에 미리 들어있는 데이터

시드는 두 경로로 들어가고, **적용 대상이 서로 다릅니다.**

| 경로 | 대상 | 적용 환경 |
| --- | --- | --- |
| Flyway `R__` SQL | 학교, 소속, 정책 | local, stage, **prod** |
| 앱 기동 시 코드 | 시즌 | local, stage (**prod 제외**) |

### 8-1. 학교 / 소속

회원가입의 `organization`, `affiliation` 에 쓸 값입니다. 아래 학교가 시드되어 있습니다.

- 성균관대학교
- 성신여자대학교
- 고려대학교

소속(학과)은 학교별로 함께 시드됩니다. 시드에 없는 값을 넣으면 가입이 막힐 수 있으니, 목록이 필요하면 백엔드에 요청하세요.

시드 SQL 은 `R__`(repeatable)이라 **파일 내용이 바뀌면 다음 배포에서 자동으로 다시 적용**됩니다. 학교나 소속이 추가되는 시점이 배포와 맞물린다는 뜻입니다.

### 8-2. 시즌

활성 시즌이 **하나도 없을 때만** 앱 기동 직후 자동으로 하나 생성됩니다.

- 기간: 기동 시점 기준 **30일 전 ~ 335일 후** (총 365일)
- 시작 시각을 과거로 잡는 이유는 **이미 진행 중인 상태**로 만들기 위해서입니다. 시작이 미래면 참가 시도가 `SEASON_NOT_JOINABLE` 로 막혀 바로 테스트할 수 없습니다.
- 활성 시즌이 이미 있으면 아무것도 하지 않습니다. 배포할 때마다 시즌이 늘어나지 않습니다.

**주의**

- 시즌을 일부러 비활성화한 뒤 재배포하면 **새 시즌이 자동으로 생성**됩니다. 시즌 종료 시나리오를 테스트했다면 배포 후 상태를 다시 확인하세요.
- 이 자동 생성은 **prod 에서는 동작하지 않습니다.** 운영 시즌은 `POST /admin/season` 으로 직접 만들어야 합니다.
