# main 도메인 테스트 발견사항

## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`MainControllerUnitTest` 3 + `MainServiceUnitTest` 4) | **7/7 통과** |
| 통합 (`MainIntegrationTest`) | **3/3 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## GET /api/v1/main

### 1. 메인 탭의 미읽음 채팅방 개수와 채팅 목록의 "보이는 방" 기준이 다르다

- **증상**: 메인 탭 배지(`unreadChatRoomCount`)가 채팅 목록에서 실제로 보이는 안읽은 방 수보다 크게 나올 수 있다. 유저가 배지를 보고 채팅 탭에 들어가도 해당 방이 없어 배지가 사라지지 않는다.
- **재현 조건**: 유저가 채팅방을 나가거나(`chat_room_participations.left_at != NULL`) 숨긴(`is_hidden = true`) 상태에서, 그 방에 읽지 않은 수신 메시지가 남아 있는 경우.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/chat/repository/ChatRepository.java:56` ~ `:63` — `countUnreadRoomsByUserId`는 `left_at`/`is_hidden`을 전혀 보지 않는다.
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:181` — 채팅 목록(`rooms`)은 `mine.getLeftAt() == null && !mine.getIsHidden()`인 방만 노출한다.
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:40` — 메인 탭이 위 카운트를 그대로 사용한다.
- **심각도**: medium
- **제안**: `countUnreadRoomsByUserId` 쿼리에 `AND p.left_at IS NULL AND p.is_hidden = FALSE`를 추가해 채팅 목록의 가시성 규칙과 한 곳(쿼리)에서 일치시킨다. 가시성 규칙이 두 군데로 갈라진 것이 근본 원인이므로, 규칙을 바꿀 때 양쪽을 함께 고치도록 테스트로 못박아두는 편이 좋다.

### 2. `unreadNotificationCount`는 현재 항상 0 (알림 피드 쓰기 경로 부재)

- **증상**: 응답의 `unreadNotificationCount`가 언제나 0이다.
- **재현 조건**: 항상. `app_notification_feeds`에 행을 INSERT 하는 운영 코드가 존재하지 않는다(`AppNotificationFeed`를 다루는 코드는 조회·읽음처리뿐이며 `save(...)` 호출부가 없다).
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:41`
  - `backend/src/main/java/com/nidus/twinly/notification/repository/AppNotificationFeedRepository.java:13` (`countByUserIdAndReadAtIsNull`) — 호출부는 `MeService.java:339`, `MeService.java:343`, `MainService.java:41` 3곳뿐이고 전부 읽기다.
- **심각도**: medium (미구현일 가능성이 높음. 버그가 아니라 "아직 안 붙인 기능"이면 무시해도 된다)
- **제안**: 알림 발송 지점(친구 요청/매칭/채팅 준비)에서 `AppNotificationFeed`를 저장하는 경로를 붙이거나, 아직 계획이 아니라면 응답 필드를 노출하지 않는 편이 프론트 혼선을 줄인다. 통합 테스트에서는 이 필드를 검증하기 위해 JDBC로 직접 행을 넣었다(`MainIntegrationTest#insertUnreadNotification`).

---

## 테스트 작성 시 메모 (버그 아님)

- `Season`은 정적 팩토리·세터가 없어 테스트에서 인스턴스를 만들 수 없다. 단위 테스트는 `BeanUtils.instantiateClass(Season.class)` + `ReflectionTestUtils.setField`로, 통합 테스트는 `id`를 설정값(`app.current-season-id=1`)에 맞춰야 해서 `JdbcTemplate`으로 직접 INSERT 했다(`id`가 `AUTO_INCREMENT`라 JPA로는 id를 지정할 수 없다).
- 통합 테스트의 진행률 검증은 정수 나눗셈 경계에서 흔들리지 않도록 시즌 구간을 중앙에서 살짝 비껴(총 200일 중 101일 경과 → 50%) 잡았다.
