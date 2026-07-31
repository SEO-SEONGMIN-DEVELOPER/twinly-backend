# chat 도메인 WebSocket(STOMP) 테스트 발견사항

담당 범위: springwolf(AsyncAPI)에 선언된 chat 도메인 WebSocket 전체.

| 방향 | 주소 | 담당 코드 |
|------|------|-----------|
| 인바운드 | `/app/chat/messages` | `ChatWebSocketController.sendMessage` |
| 인바운드 | `/app/chat/read` | `ChatWebSocketController.readMessages` |
| 아웃바운드 | `/user/queue/chat/commands` (committed/rejected × message/read = 4) | `ChatCommandResponder` |
| 아웃바운드 | `/user/queue/chat/rooms/{roomId}` | `ChatNotifier.publishMessageCreated` |
| 아웃바운드 | `/user/queue/chat/index` | `ChatNotifier.publishChatChanged` |

운영 코드는 **수정하지 않았다.** 아래는 테스트를 만들고 실행하며 발견/의심된 사항만 기록한다.

> 표기: `근거` 는 `파일:라인`. `(미검증)` 은 실행으로 확인하지 못하고 코드 정독만으로 판단한 건이다.

---

## 테스트 통과 여부

### 단위 테스트 — 전부 통과 (`./gradlew test`)

| 파일 | 케이스 | 결과 |
|------|--------|------|
| `chat/controller/ChatWebSocketControllerUnitTest.java` | 5 | ✅ 전부 통과 |
| `chat/controller/ChatCommandResponderUnitTest.java` | 7 (`@ParameterizedTest` 5건 포함 시 11) | ✅ 전부 통과 |
| `chat/notifier/ChatNotifierUnitTest.java` | 2 | ✅ 전부 통과 |

커버 내용
- 컨트롤러: 봉투 검증 → 서비스 위임 → committed/rejected 위임 분기, 봉투 type 불일치 시 예외 + 무위임
- 리스폰더: `/queue/chat/commands` 전송 주소·봉투 필드(`v`/`kind`/`type`/`commandId`), `BusinessException.status → CommandErrorCode` 매핑 (같은 422라도 전송=`TEXT_SIZE_LIMIT_EXCEEDED` / 읽음=`INVALID_MESSAGE_CURSOR` 로 갈리는 것 포함)
- 노티파이어: 참여자 팬아웃, 발신자 `ME`+`clientMsgId` / 상대 `THEM`+`clientMsgId=null` 분기, `/queue/chat/index` 전송

### 통합 테스트 — 전부 통과 (`./gradlew integrationTest`)

`chat/integration/ChatWebSocketIntegrationTest.java` (실제 소켓 + Testcontainers MySQL)

| 케이스 | 결과 |
|--------|------|
| 메시지 전송 e2e (`/app/chat/messages` → committed + created 프레임 + DB 저장) | ✅ 통과 |
| 읽음 처리 e2e (`/app/chat/read` → read committed 프레임 + 읽음 커서 DB 반영) | ✅ 통과 (**신규**) |
| 거절 e2e (없는 방 → rejected 프레임, DB 미저장) | ✅ 통과 (**신규**) |

여기서만 검증되는 것: 티켓 핸드셰이크 인증 · JSON 직렬화/역직렬화 · `/app` 라우팅 · `/user` 개인화 전달 · `@TransactionalEventListener(AFTER_COMMIT)` 실제 동작.

> **테스트 코드 쪽 결함 1건 (수정함)**: `@AfterEach` 정리 순서가 `chats` → `chat_room_participations` 였는데, `chat_room_participations.last_read_message_id` 가 `chats(id)` 를 참조(`V1__init_schema.sql:176`)하므로 FK 위반으로 실패했다. 참여 정보를 먼저 지우도록 순서를 바꿨다. 운영 코드 문제가 아니다.

### 커버하지 못한 채널

`/user/queue/chat/index` 는 **통합 테스트로 촉발할 방법이 없다.** `ChatChangedEvent` 발행처가 아직 없기 때문이며,
이는 버그가 아니라 미구현 기능이다 ([_summary.md](_summary.md)의 "다음 개발 항목 A" 참조).

---

## 발견된 운영 코드 문제 (수정하지 않음)

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
