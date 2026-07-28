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

`/user/queue/chat/index` 는 **통합 테스트로 촉발할 방법이 없다.** 이유는 아래 버그 1번.

---

## 발견된 운영 코드 문제 (수정하지 않음)

### 1. `ChatChangedEvent` 를 발행하는 곳이 없어 `/user/queue/chat/index` 채널이 영구 무음이다
- **증상**: `ChatNotifier.onChatChanged` 리스너와 `publishChatChanged` 아웃바운드는 구현되어 있고 AsyncAPI 문서에도 채널이 노출되지만, `ChatChangedEvent` 를 `publishEvent` 하는 코드가 운영 전체에 **0건**이다. 클라이언트가 `/user/queue/chat/index` 를 구독해도 프레임이 영원히 오지 않는다.
- **영향**: 채팅방 목록(인덱스) 화면이 실시간 갱신되지 않는다. 새 메시지가 와도 목록의 마지막 메시지·안읽음 수는 사용자가 직접 새로고침해야 바뀐다. 문서(스펙)와 실제 동작이 어긋나 클라이언트가 "구현했는데 안 온다"로 시간을 태우게 된다.
- **재현 조건**: `/user/queue/chat/index` 구독 후 메시지 전송 → 프레임 미수신.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/chat/notifier/ChatNotifier.java:60-66` (리스너)
  - `backend/src/main/java/com/nidus/twinly/chat/notifier/ChatNotifier.java:78-85` (아웃바운드 선언)
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:99` — 여기서 `ChatMessageCreatedEvent` 만 발행하고 `ChatChangedEvent` 는 발행하지 않는다. (`grep -rn "ChatChangedEvent" src/main/java` → 이벤트 레코드·리스너·import 뿐)
- **심각도**: **high** (스펙 대비 기능 누락)
- **제안**: `ChatService.sendMessage` 의 이벤트 발행부에서 `ChatChangedEvent` 도 함께 발행한다. 목록 갱신 트리거는 "새 메시지"와 "읽음 전진" 둘 다에서 필요하므로 `readMessages` 도 후보다. 다만 어느 시점에 목록을 갱신할지는 제품 결정이므로 먼저 정하고 붙이는 것이 맞다.
  ```java
  // ChatService.sendMessage 안, 기존 발행 바로 뒤
  eventPublisher.publishEvent(new ChatMessageCreatedEvent(chat, List.of(userId, receiverUserId)));
  eventPublisher.publishEvent(new ChatChangedEvent(roomId, List.of(userId, receiverUserId)));
  ```

### 2. `chat_room_participations` 행을 만드는 코드가 없어 `/app/chat/read` 는 운영에서 항상 실패한다
- **증상**: `ChatService.readMessages` 는 참여 정보를 필수로 조회하는데, `ChatRoomParticipation.create(...)` 를 호출하는 운영 코드가 **0건**이다(테스트 픽스처에만 존재). 실제 서비스에서는 참여 행이 절대 생기지 않으므로 읽음 처리는 항상 `CHAT_PARTICIPATION_NOT_FOUND`(404)로 거절된다.
- **동반 문제**: 같은 이유로 `ChatRoom.create(...)` 호출자도 0건이다. 매칭 성사 시 방·참여 행을 만드는 흐름 자체가 아직 없다.
- **재현 조건**: 운영 DB 상태 그대로 `/app/chat/read` 전송 → 항상 rejected.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:360-361` (참여 정보 없으면 `CHAT_PARTICIPATION_NOT_FOUND`)
  - `backend/src/main/java/com/nidus/twinly/chat/entity/ChatRoomParticipation.java:38` (`create` — 호출자 없음)
  - `backend/src/main/java/com/nidus/twinly/chat/entity/ChatRoom.java:31` (`create` — 호출자 없음)
- **심각도**: **high** (읽음 기능 전체가 동작 불가)
- **제안**: 매칭 성사 시점에 `ChatRoom` + 양쪽 `ChatRoomParticipation` 을 함께 생성하는 흐름을 추가한다. (통합 테스트는 이 픽스처를 직접 넣어 우회했으므로, 생성 흐름이 붙으면 테스트도 그 흐름을 타도록 바꾸는 것이 좋다)

### 3. HTTP status 기반 에러 매핑이라 서로 다른 원인이 같은 코드로 뭉개진다
- **증상**: `ChatCommandResponder.toCommandError` 가 `BusinessException` 의 **HTTP status만 보고** `CommandErrorCode` 를 정한다. 404는 무조건 `ROOM_NOT_FOUND` 인데, 실제로 404를 던지는 도메인 에러는 세 가지다.
  - `ROOM_NOT_FOUND` (방 없음)
  - `MATCH_NOT_FOUND` (매칭 없음) — `ErrorCode.java:60`
  - `CHAT_PARTICIPATION_NOT_FOUND` (참여 정보 없음) — `ErrorCode.java:61`

  즉 버그 2번 상황에서 클라이언트는 **"존재하지 않는 채팅방입니다"** 라는 잘못된 원인을 받는다. 방은 멀쩡히 있는데도.
- **재현 조건**: 참여 행이 없는 방에 `/app/chat/read` 전송 → `code: ROOM_NOT_FOUND` 로 rejected.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/controller/ChatCommandResponder.java:99-109` (특히 `:104` 의 `case 404 -> CommandErrorCode.ROOM_NOT_FOUND`)
- **심각도**: **medium** (오진단 유발. 클라이언트가 잘못된 복구 동작을 하게 된다)
- **제안**: status가 아니라 `ErrorCode` 자체로 매핑한다. status는 "여러 원인의 묶음"이라 1:1 대응이 성립하지 않는다.
  ```java
  private CommandErrorCode toCommandError(ErrorCode errorCode, CommandErrorCode unprocessableCode) {
      return switch (errorCode) {
          case ROOM_NOT_FOUND, MATCH_NOT_FOUND -> CommandErrorCode.ROOM_NOT_FOUND;
          case CHAT_PARTICIPATION_NOT_FOUND    -> CommandErrorCode.NOT_A_PARTICIPANT;  // 정책에 맞게
          case NOT_MATCH_PARTICIPANT           -> CommandErrorCode.NOT_A_PARTICIPANT;
          case CLIENT_MSG_ID_CONFLICT          -> CommandErrorCode.CLIENT_MSG_ID_CONFLICT;
          case MESSAGE_LENGTH_EXCEEDED, MESSAGE_NOT_IN_ROOM -> unprocessableCode;
          default                              -> CommandErrorCode.INTERNAL;
      };
  }
  ```
  (지금 매핑을 못박은 `ChatCommandResponderUnitTest` 의 `@CsvSource` 도 함께 갱신해야 한다)

### 4. 봉투 검증 실패·`@Valid` 실패 시 클라이언트가 아무 응답도 받지 못한다
- **증상**: WS 예외 핸들러가 **로그만 남기고 끝난다.** 그래서 다음 두 경우 클라이언트는 자신이 보낸 `commandId` 에 대해 committed도 rejected도 영원히 받지 못한다.
  - `@Valid` 실패 (예: `payload.text` 가 null) → `MethodArgumentNotValidException`
  - 봉투 검증 실패 (`v != 1`, `kind != command`, `type` 불일치) → `IllegalArgumentException`
- **영향**: 클라이언트는 응답 대기 타임아웃에 의존할 수밖에 없고, 원인도 알 수 없다. "성공/실패 둘 중 하나는 반드시 돌려준다"는 명령-결과 프로토콜의 전제가 깨진다.
- **재현 조건**: `/app/chat/messages` 로 `type` 을 `chat.read.advance` 로 넣은 봉투 전송 → 서버 로그에만 `[Websocket Error]` 가 찍히고 클라이언트는 무응답.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/common/websocket/handler/GlobalWebSocketExceptionHandler.java:11-14`
  - `backend/src/main/java/com/nidus/twinly/chat/controller/ChatWebSocketController.java:70-76`
- **심각도**: **medium** (정상 흐름은 멀쩡하지만 오류 상황에서 클라이언트가 멈춘다)
- **제안**: `@MessageExceptionHandler` 에서 `@Header("commandId")` 또는 실패한 메시지의 헤더로 `commandId` 를 꺼내 `INVALID_REQUEST` 성격의 rejected 프레임을 요청자에게 돌려준다. 최소한 STOMP ERROR 프레임이라도 나가게 한다. (참고: `WebSocketFrameValidationInterceptor` 가 던지는 `MessagingException` 은 인바운드 채널에서 발생하므로 ERROR 프레임이 나가고 세션이 끊긴다. 즉 프레임 크기 위반과 본문 검증 실패의 클라이언트 경험이 서로 다르다)

### 5. 멱등 재전송 경로는 committed만 돌려주고 created 이벤트를 다시 발행하지 않는다
- **증상**: 같은 `clientMsgId` 로 재전송하면 저장된 메시지를 찾아 committed를 돌려주지만 `ChatMessageCreatedEvent` 는 발행하지 않는다. 중복 저장 방지 측면에서는 옳지만, **첫 전송 때 `/user/queue/chat/rooms/{roomId}` 프레임을 놓친 클라이언트는 재전송으로도 복구되지 않는다.**
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:85-94`
- **심각도**: **low** (설계 판단 사항. 프레임 유실 복구는 원래 재연결 후 REST 재조회로 하는 것이 정석이다)
- **제안**: 현 설계 유지가 합리적이다. 다만 "프레임 유실 시 복구는 REST 재조회로 한다"를 API 문서에 명시해 두면 클라이언트가 WS만 믿지 않게 된다.

### 6. `@AsyncPublisher` 에 `payloadType` 이 없어 문서상 4개 명령 결과가 구분되지 않는다 (미검증)
- **증상**: `ChatCommandResponder` 의 아웃바운드 4개는 채널(`/user/queue/chat/commands`)도 봉투 타입(`WebSocketCommandResultBody`)도 같은데 `payloadType` 을 명시하지 않는다. 생성되는 AsyncAPI에서 committed/rejected × message/read 의 payload 스키마가 구분되지 않을 가능성이 높다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/controller/ChatCommandResponder.java:55-93`
- **심각도**: **low** (문서 품질. 런타임 동작에는 영향 없음)
- **제안**: 각 선언에 `payloadType = ChatMessageCommittedPayload.class` 처럼 명시한다. 생성 문서를 실제로 띄워 확인한 뒤 판단하는 것이 좋다.

### 7. `roomId` 를 URL 인코딩하지만 실질적으로 무의미하다
- **증상**: `ChatNotifier` 가 destination을 만들 때 `UriUtils.encodePathSegment` 로 `roomId` 를 인코딩한다. `roomId` 는 `Long` 이라 인코딩 대상 문자가 나올 수 없다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/notifier/ChatNotifier.java:40`, `:87-89`
- **심각도**: **low** (무해. 방어적 코드로 볼 수도 있다)
- **제안**: 그대로 둬도 되지만, 남긴다면 "왜 숫자에 인코딩이 필요한가"를 주석으로 남기는 편이 읽는 사람에게 낫다.
