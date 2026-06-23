# Backend ↔ Frontend (앱) 엔드포인트

---

## 1. 익명 세션 + 온보딩 (`/api/anon`, `/api/onboarding`)

가입(JWT 발급)은 **휴대폰 인증 단계**에서 일어남. 그 전까지의 온보딩 데이터는 `anonSessionToken` 으로 묶임 → signup 시 `anonSessionToken` 을 함께 넘겨 실제 계정으로 승격.

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/api/anon/start` | public | — | `{ anonSessionToken, expiresInSec }` | 익명 세션 토큰 발급 |
| POST | `/api/onboarding/basic-info` | anon-or-jwt | `{ name, gender('male'\|'female'), major, studentId(int), semester(int), birthYear, age }` | — | 기본정보 저장 |
| GET | `/api/onboarding/questions` | anon-or-jwt | — | `[{ id, dimension, scenario, options:{A,B,…}, inputType('binary'\|'single_select'\|'text_input') }]` | 가치관 설문 문항 |
| GET | `/api/onboarding/survey` | anon-or-jwt | — | `{ entries:[{ questionId, value }], completed }` | 저장된 설문 답안 (재진입 복원) |
| POST | `/api/onboarding/survey` | anon-or-jwt | `{ answers:[{ qId, value('A'\|'B') }], completed }` | `{ savedCount, completed }` | 설문 답안 저장 |
| POST | `/api/onboarding/interests` | anon-or-jwt | `{ interests:[string] }` | `{ savedCount, completed }` | 관심사 저장 |
| POST | `/api/onboarding/chat/start` | anon-or-jwt | — | `{ reply, turnIndex, shouldEnd }` | 페르소나 생성 채팅 시작 |
| POST | `/api/onboarding/chat/message` | anon-or-jwt | `{ message }` | `{ reply, turnIndex, shouldEnd }` | 채팅 턴 진행 |
| GET | `/api/onboarding/chat/history` | anon-or-jwt | — | `[{ role('user'\|'assistant'), text, turnIndex }]` | 채팅 복원 |
| POST | `/api/onboarding/chat/finish` | anon-or-jwt | — | `{ onboardingCompleted, personaId }` | 페르소나 확정 |
| POST | `/api/onboarding/profile` | anon-or-jwt | `{ heightCm, photoKey?, photoUrl? }` | — | 키/사진 키 저장 |
| POST | `/api/onboarding/profile/photo` | anon-or-jwt | multipart `file` | `{ photoUrl, photoKey }` | 프로필 사진 업로드 |
| GET | `/api/onboarding/profile/nickname/check?nickname=` | anon-or-jwt | — | `{ available, message }` | 닉네임 중복 확인 |
| POST | `/api/onboarding/profile/nickname` | anon-or-jwt | `{ nickname }` | — | 닉네임 저장 |
| POST | `/api/onboarding/phase` | jwt | `{ phase }` | — | 도달 단계 보고(기기변경 복원용) |
| GET | `/api/onboarding/state` | jwt | — | `{ completed:bool, phase:string\|null }` | 마지막 단계 조회 |

> `phase` enum: `terms, signup, survey, interests, aiChat, phoneAuth, emailAuth, heightSetup, profilePhoto, nicknameSetup, intro`

---

## 2. 인증 / 계정 발급 (`/api/auth`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/api/auth/sms/send` | public | `{ phone, purpose }` | `SmsSendResponse` | SMS 인증번호 발송 |
| POST | `/api/auth/sms/verify` | public | `{ phone, code }` | `{ smsVerifiedToken }` | 인증번호 검증 → 단기 토큰 |
| POST | `/api/auth/login` | public | `{ smsVerifiedToken }` | `AuthTokenResponse` | 기존 계정 로그인 |
| POST | `/api/auth/signup` | public | `{ smsVerifiedToken, anonSessionToken, nickname, name, birthDate, gender, consents:[], marketingConsent }` | `AuthTokenResponse` | 가입(익명세션 승격) |
| POST | `/api/auth/refresh` | public | `{ refreshToken }` | `AuthTokenResponse` | access 갱신 (401 시 자동) |
| POST | `/api/auth/logout` | public | `{ refreshToken }` | — | refresh 무효화 |
| POST | `/api/auth/email/send` | jwt | `{ email }` | `EmailSendResponse` | 학교 이메일(@korea.ac.kr) 인증코드 발송 |
| POST | `/api/auth/email/verify` | jwt | `{ email, code }` | `EmailVerifyResponse` | 이메일 인증 |

> `AuthTokenResponse = { accessToken, refreshToken, isAdmin?, adminRole?('primary'\|'demo'\|null) }`
> 이메일 인증 에러코드: `EMAIL_CODE_INVALID, EMAIL_VERIFY_EXPIRED, EMAIL_NOT_SENT, EMAIL_DOMAIN_NOT_ALLOWED`. (코드 TTL 300s, 재발송 쿨다운 60s)

---

## 3. 사이클 (`/api/cycle`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| GET | `/api/cycle/current` | jwt | — | `CycleDashboardResponse` | 현재 사이클 대시보드 (홈 폴링 + WS tick 시 재호출) |
| POST | `/api/cycle/opt-in` | jwt | `{ opt_in:bool }` | — | 참여 토글 |
| GET | `/api/cycle/progress` | jwt | — | `{ cycle_id, tracks:[…] }` | 평행우주 진행상황(스토리 트랙) |

---

## 4. 매칭 결과 / 소설 (`/api/match`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| GET | `/api/match/result` | jwt | — | `MatchResultResponse` | 이번 사이클 매칭 결과 |
| GET | `/api/match/{matchId}/partner-profile` | jwt | — | `PartnerProfileResponse` | 상대 프로필(공개 여부 반영) |
| GET | `/api/match/{matchId}/novel` | jwt | — | `NovelResponse` | POV 소설 본문 |
| POST | `/api/match/{matchId}/novel/feedback` | jwt | `{ … 피드백 }` | — | 소설 피드백 |

---

## 5. 시뮬레이션 망설임 질문 (`/api/simulation`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| GET | `/api/simulation/uncertainty/questions?…` | jwt | (쿼리) | `{ cycle_id, count, questions:[{ id, category, prompt, context?, reason?, options:[{…}], status, confidence? }] }` | 망설임 질문 목록 |
| POST | `/api/simulation/uncertainty/answers` | jwt | `{ …answers }` | — | 답변 제출(페르소나 보정) |

---

## 6. 채팅 (`/api/chat`, `/api/friends`)

채팅방은 매칭 발표(MATCH_ANNOUNCED) 시 일괄 생성됨.

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| GET | `/api/chat/rooms` | jwt | — | `ChatOverviewResponse` | 채팅방 목록(빈 방 포함) |
| GET | `/api/chat/rooms/{roomId}/messages?…` | jwt | (페이지네이션 쿼리) | `ChatMessagesResponse` | 메시지 목록 |
| POST | `/api/chat/{matchId}/messages` | jwt | `{ text, clientMsgId }` | `SendChatResponse` | 메시지 전송 (matchId 기준) |
| POST | `/api/chat/{matchId}/read` | jwt | — | — | 읽음 처리(durable, last_read 갱신) |
| POST | `/api/chat/rooms/{roomId}/leave` | jwt | — | — | 채팅방 나가기 |
| GET | `/api/friends` | jwt | — | `FriendsOverviewResponse` | 친구(공개된 매칭) 목록 |

> `matchId`(전송/읽음)와 `roomId`(목록/메시지/나가기) 키가 혼용됨 — 백엔드에서 매핑 필요.

---

## 7. 내 정보 / 프로필 / 설정 (`/api/me`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| GET | `/api/me/profile` | jwt | — | `ProfileSummaryResponse` | 프로필 요약 |
| GET | `/api/me/profile-edit` | jwt | — | `ProfileEditResponse` | 편집 화면용 현재값 |
| PATCH | `/api/me/profile` | jwt | `{ …편집필드 }` | `ProfileEditResponse` | 프로필 수정 |
| POST | `/api/me/profile/photo` | jwt | multipart `file` | `{ photoUrl, photoKey }` | 프로필 사진 교체 |
| GET | `/api/me/status` | jwt | — | `AccountStatusResponse` | 계정 상태(탈퇴 예약 등) |
| DELETE | `/api/me` | jwt | — | `{ recoverableUntil }` | 회원 탈퇴(14일 복구 가능) |
| POST | `/api/me/restore` | jwt | — | — | 탈퇴 철회 |
| GET | `/api/me/notifications` | jwt | — | `NotificationPreferencesResponse` | 알림 설정 조회 |
| PATCH | `/api/me/notifications` | jwt | `{ …prefs }` | `NotificationPreferencesResponse` | 알림 설정 변경 |
| GET | `/api/me/cycle-preferences` | jwt | — | `CyclePreferencesResponse` | 다음 사이클 자동참여 설정 |
| PATCH | `/api/me/cycle-preferences` | jwt | `{ next_cycle_auto_join:bool }` | `CyclePreferencesResponse` | 변경 |

---

## 8. 푸시 토큰 (`/api/push`)

| Method | Path | Auth | Request | 용도 |
|--------|------|------|---------|------|
| POST | `/api/push/token` | jwt | `{ fcmToken, platform }` | FCM 토큰 등록 |
| DELETE | `/api/push/token` | jwt | `{ fcmToken, platform }` | 토큰 해제 |

---

## 9. 신고 / 차단 (`/api/reports`, `/api/blocks`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/api/reports` | jwt | `{ …신고내용 }` | `ReportResponse` | 신고 |
| POST | `/api/blocks` | jwt | `{ target_user_id }` | — | 차단 |
| DELETE | `/api/blocks/{targetUserId}` | jwt | — | — | 차단 해제 |
| GET | `/api/blocks` | jwt | — | `BlockListResponse` | 차단 목록 |

---

## 10. WebSocket

| Path | 인증 | 방향 | 프레임 |
|------|------|------|--------|
| `/ws/chat/{matchId}?token=<accessToken>` | 쿼리 토큰(JWT) | S→C | `{ "type":"msg", "roomId", "matchId", "message":{ id, roomId, fromUserId, text, clientMsgId?, sentAt } }` |
| | | C→S | `{ "type":"read", "lastMsgId" }` (읽음 신호, 보조) |
| `/ws/cycle-tick` | 없음(글로벌) | S→C | 내용 무의미 — tick 수신 시 클라가 `GET /api/cycle/current` 재호출. 어드민 시계 조작 broadcast 용 |

> 둘 다 클라이언트 자동 reconnect (지수 backoff 1s→30s).

---

## 11. 내부 / dev 전용 (`/internal/dev`) — 운영에선 비활성/관리자 한정

| Method | Path | Request | 용도 |
|--------|------|---------|------|
| POST | `/internal/dev/auth/signin?as=<alias>` | `{}` | SMS 스킵 dev 로그인 → 실제 JWT |
| POST | `/internal/dev/admin/set-time` | `{ target }` | 가상 시계 set (cycle 1 리셋) |
| POST | `/internal/dev/admin/forward-1h` | `{}` | 가상 시계 +1h |
| POST | `/internal/dev/cycle/start?partners=N` | (옵션) | dev 사이클 시작 |
| POST | `/internal/dev/cycle/{cycleId}/forward-1h` | (옵션) | 특정 사이클 +1h |
