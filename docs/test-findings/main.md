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

### 1. 시즌 길이가 0이면 0으로 나누기 → 메인 탭 전체 500

- **증상**: `ArithmeticException: / by zero`가 발생해 `GlobalExceptionHandler.handleUnexpected`로 떨어지고 메인 탭 API가 500을 반환한다. 앱 첫 화면 전체가 죽는다.
- **재현 조건**: `app.current-season-id`가 가리키는 시즌의 `started_at == ended_at`인 경우. `seasons` 테이블에 `started_at < ended_at`을 강제하는 제약이 없어 운영 데이터 입력 실수로 충분히 만들어진다.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:32` (`totalMillis` 계산)
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:34` (`elapsedMillis * 100 / totalMillis`)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:499` (`seasons` 테이블에 기간 CHECK 제약 없음)
- **심각도**: low (정상 데이터에서는 발생하지 않으나, 발생 시 영향 범위가 첫 화면 전체)
- **제안**: `totalMillis <= 0`이면 진행률을 `0%`(또는 `100%`)로 처리하는 가드를 두거나, 스키마에 `CHECK (started_at < ended_at)`을 추가해 애초에 그런 시즌이 저장되지 않게 한다. 진행률 계산 자체를 `Season`의 도메인 메서드(`Season#progressPercent(Instant now)`)로 옮기면 가드 위치가 자연스럽다.

### 2. 메인 탭의 미읽음 채팅방 개수와 채팅 목록의 "보이는 방" 기준이 다르다

- **증상**: 메인 탭 배지(`unreadChatRoomCount`)가 채팅 목록에서 실제로 보이는 안읽은 방 수보다 크게 나올 수 있다. 유저가 배지를 보고 채팅 탭에 들어가도 해당 방이 없어 배지가 사라지지 않는다.
- **재현 조건**: 유저가 채팅방을 나가거나(`chat_room_participations.left_at != NULL`) 숨긴(`is_hidden = true`) 상태에서, 그 방에 읽지 않은 수신 메시지가 남아 있는 경우.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/chat/repository/ChatRepository.java:56` ~ `:63` — `countUnreadRoomsByUserId`는 `left_at`/`is_hidden`을 전혀 보지 않는다.
  - `backend/src/main/java/com/nidus/twinly/chat/service/ChatService.java:181` — 채팅 목록(`rooms`)은 `mine.getLeftAt() == null && !mine.getIsHidden()`인 방만 노출한다.
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:40` — 메인 탭이 위 카운트를 그대로 사용한다.
- **심각도**: medium
- **제안**: `countUnreadRoomsByUserId` 쿼리에 `AND p.left_at IS NULL AND p.is_hidden = FALSE`를 추가해 채팅 목록의 가시성 규칙과 한 곳(쿼리)에서 일치시킨다. 가시성 규칙이 두 군데로 갈라진 것이 근본 원인이므로, 규칙을 바꿀 때 양쪽을 함께 고치도록 테스트로 못박아두는 편이 좋다.

### 3. `unreadNotificationCount`는 현재 항상 0 (알림 피드 쓰기 경로 부재)

- **증상**: 응답의 `unreadNotificationCount`가 언제나 0이다.
- **재현 조건**: 항상. `app_notification_feeds`에 행을 INSERT 하는 운영 코드가 존재하지 않는다(`AppNotificationFeed`를 다루는 코드는 조회·읽음처리뿐이며 `save(...)` 호출부가 없다).
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:41`
  - `backend/src/main/java/com/nidus/twinly/notification/repository/AppNotificationFeedRepository.java:13` (`countByUserIdAndReadAtIsNull`) — 호출부는 `MeService.java:339`, `MeService.java:343`, `MainService.java:41` 3곳뿐이고 전부 읽기다.
- **심각도**: medium (미구현일 가능성이 높음. 버그가 아니라 "아직 안 붙인 기능"이면 무시해도 된다)
- **제안**: 알림 발송 지점(친구 요청/매칭/채팅 준비)에서 `AppNotificationFeed`를 저장하는 경로를 붙이거나, 아직 계획이 아니라면 응답 필드를 노출하지 않는 편이 프론트 혼선을 줄인다. 통합 테스트에서는 이 필드를 검증하기 위해 JDBC로 직접 행을 넣었다(`MainIntegrationTest#insertUnreadNotification`).

### 4. 현재 시즌 설정이 어긋나면 첫 화면 전체가 500

- **증상**: `app.current-season-id`가 가리키는 시즌 행이 없으면 `IllegalStateException` → 500(`INTERNAL_ERROR`).
- **재현 조건**: 시즌 전환 시 설정값만 올리고 `seasons` 행을 넣지 않은 경우, 또는 배포 환경별로 설정값이 다른 경우.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:28` ~ `:29`
- **심각도**: low (예외 타입 자체는 "서버 설정 오류"라는 의미로 적절하다. 다만 폭발 반경이 크다)
- **제안**: 예외 처리 방식은 지금이 맞다고 본다. 대신 애플리케이션 기동 시점에 현재 시즌 존재 여부를 한 번 검증(fail-fast)하면 런타임에 첫 화면이 죽는 대신 배포 시점에 잡힌다. `app.current-season-id`를 쓰는 서비스가 `MainService`/`ChatService`/`ActivityService`/`SeasonService` 4곳이므로 공통 조회 컴포넌트로 묶는 것도 방법이다.

### 5. 조회 3건이 서로 다른 트랜잭션/스냅샷에서 실행된다

- **증상**: 시즌 조회·안읽은 채팅방 수·안읽은 알림 수가 각각 별도 커넥션(auto-commit)에서 실행되어, 응답 안의 `serverNow`와 카운트가 미세하게 다른 시점의 상태를 담을 수 있다.
- **재현 조건**: 세 쿼리 사이에 데이터가 바뀌는 경우. 실사용상 체감되진 않는다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:27` (`mainTab`에 `@Transactional` 없음)
- **심각도**: low (조회 메서드에 `@Transactional`을 붙이지 않는 것이 이 프로젝트의 기존 컨벤션이라 일관성은 유지되고 있다)
- **제안**: 지금 당장 고칠 필요는 없다. 다만 커넥션 획득 3회를 1회로 줄이고 스냅샷을 맞추고 싶다면 `@Transactional(readOnly = true)`를 붙이는 정도로 충분하다.

### 6. `@Value` 필드 주입이라 순수 단위 테스트에서 설정값을 직접 넣어야 한다

- **증상**: `currentSeasonId`가 생성자 주입이 아니라 필드 `@Value`라, Mockito 순수 단위 테스트에서 `ReflectionTestUtils.setField(mainService, "currentSeasonId", 1L)`로 채워야 한다(테스트가 필드 이름이라는 구현 세부에 결합된다).
- **재현 조건**: 항상.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/main/service/MainService.java:20` ~ `:21`
- **심각도**: low
- **제안**: 급하지 않다. 4번 제안대로 현재 시즌 조회를 공통 컴포넌트로 빼면서 생성자 주입(또는 `@ConfigurationProperties` 레코드)으로 바꾸면 자연스럽게 해소된다.

---

## 테스트 작성 시 메모 (버그 아님)

- `Season`은 정적 팩토리·세터가 없어 테스트에서 인스턴스를 만들 수 없다. 단위 테스트는 `BeanUtils.instantiateClass(Season.class)` + `ReflectionTestUtils.setField`로, 통합 테스트는 `id`를 설정값(`app.current-season-id=1`)에 맞춰야 해서 `JdbcTemplate`으로 직접 INSERT 했다(`id`가 `AUTO_INCREMENT`라 JPA로는 id를 지정할 수 없다).
- 통합 테스트의 진행률 검증은 정수 나눗셈 경계에서 흔들리지 않도록 시즌 구간을 중앙에서 살짝 비껴(총 200일 중 101일 경과 → 50%) 잡았다.
