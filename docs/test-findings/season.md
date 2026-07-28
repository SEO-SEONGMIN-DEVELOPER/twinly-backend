# season 도메인 테스트 발견사항

대상 엔드포인트

- `PUT /api/v1/season/participation`
- `GET /api/v1/season/participation`

작성한 테스트

- `backend/src/test/java/com/nidus/twinly/season/controller/SeasonControllerUnitTest.java`
- `backend/src/test/java/com/nidus/twinly/season/service/SeasonServiceUnitTest.java`
- `backend/src/test/java/com/nidus/twinly/season/integration/SeasonIntegrationTest.java`

> 운영 코드는 수정하지 않았습니다. 아래는 기록만 한 발견사항입니다.

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`SeasonControllerUnitTest` 6 + `SeasonServiceUnitTest` 7) | **13/13 통과** |
| 통합 (`SeasonIntegrationTest`) | **4/4 통과** |

### 이번 라운드의 테스트 수정 (운영 코드 아님)

`participation_when_participated_end_to_end`가 빨간불이었는데, **테스트 기대값이 틀린 경우**였다.

- 기대: `2026-07-01T00:00:00Z` / 실제: `2026-07-01T09:00:00+09:00`
- 운영 코드의 `KstInstantSerializer`가 모든 `Instant`를 **KST 오프셋으로 직렬화**하는 것이 의도된 설계다.
  (`backend/src/main/java/com/nidus/twinly/common/jackson/KstInstantSerializer.java`,
  `JacksonTimeConfig.java`에서 전역 모듈로 등록)
- 따라서 운영 코드를 건드리지 않고 테스트 기대값을 실제 계약에 맞췄다.

> 참고: 이 직렬화 규칙은 **전역**이다. `Instant`를 내려보내는 모든 응답이 `+09:00` 오프셋으로 나가므로,
> 다른 도메인의 테스트를 쓸 때도 `...Z`를 기대하면 안 된다.

---

## PUT /api/v1/season/participation

### 1. check-then-act 경합으로 동시 요청 시 500이 난다

- **증상**: 같은 유저의 참가 요청이 동시에 2건 들어오면 두 요청 모두 `existsByUserIdAndSeasonId`에서 `false`를 받고 각각 `save`를 시도한다. 두 번째 insert가 유니크 제약 `uk_season_participations_user_id_season_id`를 위반해 `DataIntegrityViolationException`이 발생하고, `GlobalExceptionHandler.handleUnexpected`가 이를 잡아 **500 INTERNAL_ERROR**를 반환한다. 멱등하게 처리하려던 의도(이미 참가했으면 조용히 return)가 동시성 상황에서만 깨진다.
- **재현 조건**: 참가 기간 중인 시즌에서 동일 userId로 `PUT /api/v1/season/participation`를 동시에 2회 호출(버튼 더블클릭, 클라이언트 재시도, 모바일 네트워크 재전송).
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:38` (exists 체크)
  - `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:42` (save)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:496` (유니크 제약)
- **심각도**: medium
- **제안**: DB 유니크 제약이 최후의 방어선 역할을 이미 하고 있으므로, 그 예외를 "이미 참가함"으로 해석해 멱등 성공으로 흡수하는 것이 가장 저렴하다.
  ```java
  try {
      seasonParticipationRepository.save(SeasonParticipation.create(userId, currentSeasonId));
  } catch (DataIntegrityViolationException e) {
      // 동시 요청으로 이미 참가 행이 생성된 경우 → 멱등 성공
  }
  ```
  (단, 참여 트랜잭션 안에서 예외를 삼키면 rollback-only 문제가 생길 수 있으므로 저장 지점을 별도 트랜잭션 경계로 분리하거나 컨트롤러 레벨에서 처리하는 방식도 함께 검토 필요)

### 2. 현재 시즌 설정이 잘못되면 런타임 500으로만 드러난다

- **증상**: `app.current-season-id`가 가리키는 시즌 행이 없으면 `IllegalStateException`이 던져져 500 INTERNAL_ERROR가 된다. 설정 오류이므로 500 자체는 타당하지만, **기동 시점이 아니라 유저 요청 시점에야** 드러난다. 실제로 마이그레이션(`V1__init_schema.sql`)에 seasons 시드 데이터가 전혀 없어서, 시즌 행을 수동으로 넣지 않으면 이 경로가 곧바로 터진다.
- **재현 조건**: seasons 테이블에 id=1 행이 없는 상태에서 인증된 유저가 참가 API 호출.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:29`
- **심각도**: low
- **제안**: 기동 시 현재 시즌 존재를 검증(`ApplicationRunner`/`@PostConstruct`)해 fail-fast 하거나, 마이그레이션에 시즌 시드를 추가한다.

### 3. `SeasonParticipation.create`가 `Instant.now()`를 두 번 호출한다

- **증상**: `participatedInAt`과 `createdAt`이 서로 다른 시각(수 마이크로초~밀리초 차이)으로 저장된다. 동일 이벤트의 두 시각이 어긋나 로그·통계 대조 시 혼란을 준다.
- **재현 조건**: 참가 성공 후 두 컬럼 값 비교.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/entity/SeasonParticipation.java:35`
- **심각도**: low
- **제안**: `Instant now = Instant.now();`를 한 번만 구해 두 필드에 대입한다.

### 4. `currentSeasonId`가 `@Value` 필드 주입이다

- **증상**: 생성자 주입이 아니라 필드 주입이라, 순수 단위 테스트에서 `ReflectionTestUtils.setField`로 강제 주입하지 않으면 `currentSeasonId`가 `null`이 되어 NPE/오동작한다(실제로 `SeasonServiceUnitTest`에서 그렇게 처리했다). 불변성·테스트 용이성 모두 손해다.
- **재현 조건**: `new SeasonService(seasonRepository, seasonParticipationRepository)`로 생성 후 `participateIn` 호출.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:21`
- **심각도**: low
- **제안**: 생성자 파라미터로 `@Value("${app.current-season-id}") Long currentSeasonId`를 받거나, 시즌 설정을 `@ConfigurationProperties` 타입으로 묶어 주입한다. (같은 패턴이 `ChatService`, `ActivityService`, `MainService`에도 반복되고 있어 공통 정리 대상)

---

## GET /api/v1/season/participation

### 5. 조회 메서드에 트랜잭션 경계가 없다

- **증상**: `participation()`에 `@Transactional(readOnly = true)`가 없다. 현재는 리포지토리 단건 조회 1회뿐이라 실질적 문제는 없지만, `participateIn()`에만 트랜잭션이 붙어 있어 서비스 내 경계가 비일관적이다. 이후 조회가 2개 이상으로 늘어나면 읽기 일관성이 보장되지 않는다.
- **재현 조건**: 정적 검토(동작 이상 없음).
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:45`
- **심각도**: low
- **제안**: `@Transactional(readOnly = true)`를 붙여 경계와 읽기 전용 최적화를 명시한다.

### 6. 존재하지 않는 시즌 id에 대해 POST와 GET의 동작이 비일관적이다

- **증상**: `app.current-season-id`가 가리키는 시즌이 없을 때, POST는 500을 던지는 반면 GET은 시즌 존재 여부를 확인하지 않고 `{"currentSeasonId":"1","participatedInAt":null}`로 **200을 반환**한다. 클라이언트 입장에서 "존재하는 시즌인데 아직 미참가"와 "시즌 설정이 깨짐"이 구분되지 않는다.
- **재현 조건**: seasons에 id=1 행이 없는 상태에서 `GET /api/v1/season/participation` 호출.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/service/SeasonService.java:46`
- **심각도**: low
- **제안**: 2번(기동 시 fail-fast)을 적용하면 이 비일관성도 함께 사라진다. 별도 처리를 추가할 필요는 없다.
