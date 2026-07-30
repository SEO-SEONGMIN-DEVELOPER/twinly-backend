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

### 2. `SeasonParticipation.create`가 `Instant.now()`를 두 번 호출한다

- **증상**: `participatedInAt`과 `createdAt`이 서로 다른 시각(수 마이크로초~밀리초 차이)으로 저장된다. 동일 이벤트의 두 시각이 어긋나 로그·통계 대조 시 혼란을 준다.
- **재현 조건**: 참가 성공 후 두 컬럼 값 비교.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/season/entity/SeasonParticipation.java:35`
- **심각도**: low
- **제안**: `Instant now = Instant.now();`를 한 번만 구해 두 필드에 대입한다.

---

