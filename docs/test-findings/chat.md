# chat 도메인 테스트 발견사항

담당 범위: `ChatController` (REST 8개 엔드포인트). WebSocket 컨트롤러는 이번 범위 제외.
운영 코드는 수정하지 않았고, 아래는 테스트 작성 중 코드를 읽으며 발견/의심된 사항만 기록한다.

> 표기: `근거` 는 `파일:라인`. `(미검증)` 표시가 있는 항목은 실행으로 확인하지 못하고 코드 정독만으로 판단한 건이다.

---

# [이번 라운드] springdoc 스펙 기준 통합 커버리지 확장

통합 테스트를 3건 → 13건으로 늘려 chat REST 8개 오퍼레이션을 전부 관통시켰다.
**새로 확인된 운영 코드 버그는 없다.**

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`ChatControllerUnitTest` 13 + `ChatServiceUnitTest` 17 + 그 외 17) | **47/47 통과** |
| 통합 (`ChatIntegrationTest`) | **13/13 통과** |

## 추가한 통합 테스트

| 오퍼레이션 | 검증 내용 |
|---|---|
| `GET /chat/rooms/{roomId}` | 입장 동의 상태가 각자의 DB 상태대로 응답 / 매칭 당사자 아니면 403 `NOT_MATCH_PARTICIPANT` / 없는 방이면 404 `ROOM_NOT_FOUND` |
| `POST /chat/rooms/{roomId}/hide` | `isHidden=true` 저장 + **목록 조회에서 실제로 제외**되는지까지 확인 |
| `POST /chat/rooms/{roomId}/leave` | `leftAt` 기록 |
| `GET /chat/rooms/{roomId}/messages` | 커서 쿼리가 id 내림차순으로 동작 / 발신자 구분 ME·THEM / `limit` 초과 시 `hasMore=true` + `nextCursor` |
| `POST /chat/rooms/{roomId}/read` | 읽음 포인터 전진 + **목록의 안읽음 수가 0으로 집계**되는지까지 확인 / 방에 없는 메시지 id면 422 `MESSAGE_NOT_IN_ROOM` |

## 확인된 직렬화 계약 (테스트 기대값 정정 사유)

통합 테스트 작성 중 기대값을 두 번 정정했다. **둘 다 운영 코드가 옳고 테스트가 틀린 경우**였으며,
문서화 가치가 있어 남긴다.

- `senderType`은 대문자 enum명이 아니라 **소문자**로 직렬화된다 (`me` / `them` / `system`).
  `ChatSenderType`의 각 상수에 `@JsonProperty("me")` 등이 붙어 있다.
- 채팅방 상세의 상대 정보 필드명은 `partner.nickname`이 아니라 **`partner.userName`**이다.
  값은 `User.getNickname()`에서 채워지므로 필드명과 출처가 어긋나 있다.
  (`ChatRoomDetailPartnerResponse.java` / `ChatService.java:263`)
  동작 결함은 아니지만, 이름만 보고는 실명이 내려간다고 오해하기 쉽다. **심각도: info**

---

## GET /api/v1/chat/rooms

### 1. 같은 방에서 `sent_at`이 동일한 메시지가 2건이면 500 (미검증)
- **증상**: `findLatestByRoomIdIn`은 방별 `MAX(sent_at)`과 조인하므로 동일 `sent_at`이 2건이면 같은 `room_id`로 두 행이 나오고, `Collectors.toMap(Chat::getRoomId, ...)`이 duplicate key로 `IllegalStateException` → 500.
- **재현 조건**: 같은 방에서 마이크로초까지 동일한 시각에 2건이 저장(동시 전송 / 시계 해상도가 낮은 환경).
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:149-150`, `backend/src/main/java/com/nidus/twinly/chat/repository/ChatRepository.java` (`findLatestByRoomIdIn`)
- **심각도**: **low** (확률은 낮지만 터지면 목록 전체가 500)
- **제안**: `MAX(sent_at)` 대신 `MAX(id)` 기준으로 최신 메시지를 뽑거나(단조 증가라 tie가 없다), `toMap`에 merge 함수 `(a, b) -> a`를 준다.

---

## GET /api/v1/chat/rooms/{roomId}/messages

### 2. `limit` 값 검증이 없어 `limit=0`이면 500, 음수면 SQL 오류
- **증상**
  - `limit=0`: `effectiveLimit=0` → `LIMIT 1`로 1건을 읽어오고 `hasMore=true`가 되지만 `pageChats = chats.subList(0, 0)` 이 빈 리스트라 `pageChats.getLast()`에서 `NoSuchElementException` → 500.
  - `limit=-1`: `LIMIT 0`(=`effectiveLimit+1`)이 되어 결과가 항상 비고, 더 작은 음수(`limit=-2` 등)는 `LIMIT -1` 같은 SQL이 만들어져 문법 오류 → 500.
  - 상한도 없어 `limit=1000000` 같은 요청이 그대로 DB로 전달된다.
- **재현 조건**: `GET /api/v1/chat/rooms/{roomId}/messages?limit=0` (방에 메시지가 1건 이상 있을 때)
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:322`, `:326`, `:328`, `:340`
- **심각도**: **medium** (클라이언트 실수만으로 500이 나고, 서버 방어가 전혀 없다)
- **제안**: 컨트롤러에서 `@RequestParam` 에 `@Positive @Max(100)` 등을 걸거나, 서비스 진입부에서 `limit`을 `1..MAX` 범위로 clamp 한다. 최소한 `effectiveLimit <= 0`이면 `INVALID_REQUEST`로 400 처리.

---

## POST /api/v1/chat/rooms/{roomId}/enter, POST /api/v1/chat/rooms/{roomId}/hide, POST /api/v1/chat/rooms/{roomId}/leave

### 3. 참여 정보가 없을 때 `IllegalStateException`으로 500이 나고, `/read`와 처리 방식이 불일치한다
- **증상**: 방은 있는데 `chat_room_participations` 행이 없으면 `IllegalStateException`이 던져져 500(INTERNAL_ERROR). 같은 상황에서 `POST /read`는 `CHAT_PARTICIPATION_NOT_FOUND`(404)로 처리한다.
- **재현 조건**: 채팅방 생성 트랜잭션이 부분 실패했거나 참여 행이 유실된 상태에서 enter/hide/leave 호출.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:290` (enterRoom)
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:309` (hideRoom)
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:383` (leaveRoom)
  - 대비: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:359-360` (readMessages는 `CHAT_PARTICIPATION_NOT_FOUND`)
- **심각도**: **medium**
- **제안**: 세 곳 모두 `BusinessException(ErrorCode.CHAT_PARTICIPATION_NOT_FOUND)`로 통일하고, 각 엔드포인트의 `@ApiResponses`에 404 사유로 `CHAT_PARTICIPATION_NOT_FOUND`를 추가한다. "일어나면 안 되는 상태"라도 5xx보다 도메인 에러코드가 운영·클라이언트 대응에 유리하다.

---

## POST /api/v1/chat/rooms/{roomId}/messages

### 4. 닫힌 방·나간 방에도 메시지를 보낼 수 있다
- **증상**: `sendMessage`는 `room.closedAt`과 내 참여 정보의 `leftAt`을 전혀 확인하지 않는다. 종료된 채팅방이나 이미 나간 방에도 메시지가 저장된다.
- **재현 조건**: `POST /leave` 후(또는 `chat_rooms.closed_at`이 채워진 뒤) 같은 방으로 메시지 전송.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:71-101` (방/매칭 조회 후 참여자 여부만 검사)
- **심각도**: **medium** (정책 확정 전이면 low)
- **제안**: 닫힌 방/나간 방에 대한 정책을 정하고, 차단할 거라면 전용 ErrorCode(예: `ROOM_CLOSED`)를 추가해 403/409로 처리한다. 지금 당장 정책이 없다면 최소한 TODO로 남길 것.

### 5. 본문 길이 검사가 권한 검사보다 먼저 수행된다
- **증상**: 4KB 초과 본문이면 방 존재 여부·참여자 여부를 확인하기 전에 422(`MESSAGE_LENGTH_EXCEEDED`)를 반환한다. 존재하지 않는 방/남의 방에 대해서도 422가 나가므로 404/403과 응답이 갈린다(방 존재 여부를 유추할 수 있는 미세한 정보 노출).
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:72-77`
- **심각도**: **low**
- **제안**: 인가(방 조회 → 참여자 확인) 이후에 본문 길이를 검사한다.

### 6. `text`에 `@NotBlank`/`@Size`가 없어 빈 문자열 메시지가 저장된다
- **증상**: `{"text":"","clientMsgId":"..."}` 가 `@NotNull`만 통과해 빈 메시지가 그대로 저장되고, 목록의 `preview`도 빈 문자열이 된다. `clientMsgId`도 길이 제한이 없다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/dto/request/ChatSendMessageRequest.java:6-9`
- **심각도**: **low**
- **제안**: `text`에 `@NotBlank`, `clientMsgId`에 `@NotBlank @Size(max = 255)`를 건다. (`chats` 유니크 인덱스가 `client_msg_id(255)` 접두어라 255를 넘으면 서로 다른 id가 충돌로 잡힐 수 있다 — `backend/src/main/resources/db/migration/V1__init_schema.sql:209`)

---

## POST /api/v1/chat/rooms/{roomId}/read

### 7. 서비스가 확정한 읽음 포인터를 REST 응답에서 버린다
- **증상**: `ChatService.readMessages`는 `ChatReadMessagesResult(roomId, confirmed)`를 돌려주지만 컨트롤러가 `void`라 클라이언트는 서버가 확정한 포인터를 알 수 없다. WebSocket 경로(`CHAT_READ_COMMITTED`)는 이 값을 내려주므로 두 경로의 계약이 다르다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/controller/ChatController.java:99-103`
- **심각도**: **low**
- **제안**: 응답 DTO를 만들어 `lastMessageId`를 내려주면 클라이언트가 로컬 상태를 서버 기준으로 맞출 수 있다. (지금 필요 없다면 그대로 둬도 무방)

### 8. `@Modifying` 네이티브 UPDATE 이후 영속성 컨텍스트가 갱신되지 않는다
- **증상**: `advanceReadPointer`는 네이티브 UPDATE라 같은 트랜잭션의 영속성 컨텍스트에 있는 `ChatRoomParticipation` 엔티티는 이전 값을 그대로 들고 있다. 현재 흐름에서는 직후에 트랜잭션이 끝나 실피해는 없지만, 같은 트랜잭션에서 참여 정보를 다시 읽는 로직이 추가되면 즉시 스테일 데이터를 보게 된다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:359-363`, `backend/src/main/java/com/nidus/twinly/chat/repository/ChatRoomParticipationRepository.java` (`advanceReadPointer`)
- **심각도**: **low**
- **제안**: `@Modifying(clearAutomatically = true, flushAutomatically = true)` 를 붙인다.
- **비고**: 통합 테스트에서도 이 특성 때문에 DB 반영 검증 시 `EntityManager.clear()` 후 다시 읽어야 한다(입장 테스트에 해당 처리를 넣어 두었다).

---

## 도메인 공통

### 9. `ChatChangedEvent`가 어디서도 발행되지 않는다 (죽은 알림 경로)
- **증상**: `ChatNotifier.onChatChanged`가 `/user/queue/chat/index`로 `CHAT_CHANGED`를 보내도록 되어 있지만, `ChatChangedEvent`를 `publishEvent` 하는 코드가 프로젝트 전체에 없다. 즉 채팅 목록 화면의 실시간 갱신 신호가 한 번도 나가지 않는다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/notifier/ChatNotifier.java:52-60` (리스너), `backend/src/main/java/com/nidus/twinly/chat/event/ChatChangedEvent.java` (발행처 없음). `ChatService.sendMessage`는 `ChatMessageCreatedEvent`만 발행한다 (`ChatService.java:98`).
- **심각도**: **medium** (기능 미구현 / 죽은 코드)
- **제안**: 메시지 생성·읽음 처리·방 상태 변경 시점에 `ChatChangedEvent`를 발행하도록 연결하거나, 아직 쓰지 않을 거면 리스너와 이벤트를 제거해 죽은 코드를 남기지 않는다.

### 10. 조회 전용 메서드에 `@Transactional(readOnly = true)`가 없다
- **증상**: `rooms`, `roomDetail`, `messages`는 트랜잭션 없이 실행되어 메서드 안의 여러 쿼리가 서로 다른 트랜잭션/커넥션에서 수행된다. 목록 조회는 8개 이상의 쿼리를 묶어 쓰므로 중간 상태가 섞여 보일 수 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:115` (`rooms`), `:226` (`roomDetail`), `:313` (`messages`)
- **심각도**: **low**
- **제안**: 조회 메서드에 `@Transactional(readOnly = true)`를 붙여 읽기 일관성과 커넥션 재사용을 확보한다.

---

## 작성한 테스트에서 의도적으로 제외한 케이스

아래 케이스는 **위 버그 때문에 현재 실패하거나 500이 나므로** 테스트로 고정하지 않았다. 운영 코드 수정 후 추가할 것.

| 케이스 | 관련 항목 |
| --- | --- |
| `GET /rooms` — 메시지가 없는 방이 포함된 목록 | 1 |
| `GET /rooms` — 매칭이 0건인 유저의 빈 목록 | 2 |
| `GET /rooms/{roomId}/messages?limit=0` | 4 |
| enter/hide/leave — 참여 정보가 없을 때의 상태 코드 | 5 |
| `POST /messages` — 닫힌 방/나간 방으로의 전송 차단 | 6 |
