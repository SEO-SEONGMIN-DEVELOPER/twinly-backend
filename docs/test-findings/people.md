# people 도메인 테스트 발견사항

대상 컨트롤러: `backend/src/main/java/com/nidus/twinly/people/controller/PeopleController.java`
대상 서비스: `backend/src/main/java/com/nidus/twinly/people/service/PeopleService.java`

> 운영 코드는 수정하지 않았고, 테스트 작성 중 발견한 버그/의심 지점만 기록한다.

---

## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`PeopleControllerUnitTest` 14 + `PeopleServiceUnitTest` 19) | **33/33 통과** |
| 통합 (`PeopleIntegrationTest`) | **8/8 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 8개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---


## GET /api/v1/people

### 1. 탈퇴 유저·차단 유저가 목록에서 걸러지지 않는다
- 증상: 탈퇴(`users.deleted_at != null`)한 상대, 내가 차단한 상대가 그대로 목록에 노출된다. `profile` 응답에는 `isDeleted`/`isBlocked`가 있는데 목록 아이템(`PeopleItemResult`)에는 두 필드가 아예 없어 클라이언트가 구분할 수도 없다.
- 재현 조건: 관계(`relationships`)가 있는 상대를 차단하거나 그 상대가 탈퇴한 뒤 `GET /api/v1/people` 호출.
- 근거 코드 위치: `people/service/PeopleService.java:77-144` (파트너 id 조회 후 `deletedAt`/`blockRepository` 필터링 없음), `people/dto/result/PeopleItemResult.java:6-17`
- 심각도: medium
- 제안: `blockRepository.findAllByUserId(userId)`로 차단 대상을 제외하거나, 최소한 `isDeleted`/`isBlocked`를 아이템에 포함시켜 클라이언트가 판단할 수 있게 한다. (`blockRepository`는 이미 `PeopleService`에 주입되어 있으나 `people()`에서는 쓰이지 않는다.)

### 2. 상대 이름 표기 규칙이 API마다 다르다
- 증상: 목록은 `givenName`만(`"길동"`), `profile`/`events`는 `familyName + givenName`(`"홍길동"`)을 내려준다. 같은 상대가 화면마다 다른 이름으로 보인다.
- 재현 조건: 같은 상대에 대해 `GET /api/v1/people`와 `GET /api/v1/people/{userId}/profile`을 비교.
- 근거 코드 위치: `people/service/PeopleService.java:128` vs `people/service/PeopleService.java:180`, `people/service/PeopleService.java:277`
- 심각도: medium
- 제안: 표기 규칙을 한 곳(예: `User#displayName()`)으로 모아 전 API가 같은 규칙을 쓰게 한다.

### 3. limit 상한이 없다
- 증상: `limit=1000000`처럼 큰 값을 주면 그대로 `LIMIT 1000001`이 나가고, 이어서 유저/사진/관계/매칭/씬/즐겨찾기 6개 쿼리가 전부 그 크기의 `IN` 절로 실행된다.
- 재현 조건: `GET /api/v1/people?limit=1000000`
- 근거 코드 위치: `people/service/PeopleService.java:78` (`(limit != null && limit > 0) ? limit : DEFAULT_LIMIT` — 상한 없음)
- 심각도: medium
- 제안: `Math.min(limit, MAX_LIMIT)` 형태로 상한을 두거나 컨트롤러에서 `@Max`로 검증한다. `events`의 `limit`(`PeopleService.java:282`)도 동일하다.

---

## GET /api/v1/people/{userId}/profile

### 4. 탈퇴한 상대의 실명이 그대로 내려간다
- 증상: `isDeleted=true`인 상대여도 `userName`에 실제 성+이름이 그대로 담긴다. (차단 도메인에서는 탈퇴 유저를 "탈퇴한 사용자"로 마스킹한다 — `block/service/BlockService`)
- 재현 조건: 상대 유저의 `deleted_at`을 채운 뒤 프로필 조회.
- 근거 코드 위치: `people/service/PeopleService.java:180`, `people/service/PeopleService.java:188`
- 심각도: medium
- 제안: 탈퇴 유저는 이름/사진/공개 필드를 마스킹해 내려준다. (block 도메인과 규칙을 통일)

### 5. `familyName`/`givenName`이 null이면 `"nullnull"` 문자열이 만들어진다
- 증상: 문자열 `+` 연결이라 null이 `"null"`로 직렬화되어 응답에 `"nullnull"`이 나갈 수 있다.
- 재현 조건: 개인정보 파기 등으로 이름 컬럼이 비워진 유저의 프로필/이벤트 조회.
- 근거 코드 위치: `people/service/PeopleService.java:180`, `people/service/PeopleService.java:277`, `people/service/PeopleService.java:381`
- 심각도: low
- 제안: null-safe한 이름 조합 유틸을 쓴다.

---

## PUT /api/v1/people/{userId}/favorite

### 6. 조회-후-저장 경로에 트랜잭션이 없다
- 증상: `PeopleService`의 어떤 메서드에도 `@Transactional`이 없다. `favorite()`는 "선호도 조회 → 없으면 생성 → 저장"인데 조회와 저장이 서로 다른 트랜잭션에서 실행되므로, 동시에 두 요청이 들어오면 둘 다 "없음"으로 판단해 INSERT를 시도하고 `uk_encounter_preferences_encounter_id_user_id` 유니크 위반으로 한쪽이 500이 된다.
- 재현 조건: 같은 (encounter, user)에 대해 즐겨찾기 등록을 동시에 두 번 호출.
- 근거 코드 위치: `people/service/PeopleService.java:193-202`, 스키마 `db/migration/V1__init_schema.sql:265`
- 심각도: medium
- 제안: `favorite()`/`deleteFavorite()`에 `@Transactional`을 붙이고, 유니크 위반은 멱등 처리(재조회 후 갱신)한다.

---

## DELETE /api/v1/people/{userId}/favorite

### 7. 발견된 이슈 없음
- 선호도 행이 없으면 아무것도 하지 않는 멱등 동작이며, 의도에 맞는다. (단, 6번의 트랜잭션 부재는 동일하게 적용된다.)

---

## GET /api/v1/people/{userId}/intimacy-series

### 8. `maxPoints`에 검증이 없어 음수를 주면 500이 난다
- 증상: `maxPoints`가 음수면 `new ArrayList<>(maxPoints)`에서 `IllegalArgumentException: Illegal Capacity`가 발생하고, 전역 핸들러의 `@ExceptionHandler(Exception.class)`에 잡혀 500 `INTERNAL_ERROR`가 나간다. `maxPoints=0`이면 예외 없이 **조용히 빈 시계열**이 내려간다(에러도 아니고 데이터도 없음).
- 재현 조건: `GET /api/v1/people/{userId}/intimacy-series?from=...&to=...&resolution=DAY&maxPoints=-1`
- 근거 코드 위치: `people/service/PeopleService.java:248-260` (`downsample`), 컨트롤러 파라미터 `people/controller/PeopleController.java:68` (검증 애노테이션 없음)
- 심각도: high
- 제안: 컨트롤러에서 `@Min(1) @Max(...)`로 검증하거나(클래스에 `@Validated` 필요), 서비스에서 `maxPoints <= 0`이면 다운샘플링을 건너뛴다.

### 9. 필수 쿼리 파라미터가 빠지면 400이 아니라 500이 나간다 (전역 이슈, 이 API가 가장 크게 노출)
- 증상: `from`/`to`/`resolution`/`maxPoints`는 모두 필수인데, 하나라도 빠지면 Spring이 `MissingServletRequestParameterException`을 던진다. `GlobalExceptionHandler`에 이 예외(또는 상위 `ServletRequestBindingException`) 전용 핸들러가 없고 `@ExceptionHandler(Exception.class)`가 있어, `ExceptionHandlerExceptionResolver`가 `DefaultHandlerExceptionResolver`(400 매핑)보다 먼저 이 catch-all을 선택한다. 결과적으로 400이어야 할 요청이 500 `INTERNAL_ERROR`로 응답된다.
- 재현 조건: `GET /api/v1/people/{userId}/intimacy-series?from=2026-07-01T00:00:00Z&to=2026-07-31T00:00:00Z&resolution=DAY` (maxPoints 누락)
- 근거 코드 위치: `common/web/GlobalExceptionHandler.java:44-52` (타입 불일치만 처리), `common/web/GlobalExceptionHandler.java:64-69` (catch-all)
- 심각도: high
- 제안: `GlobalExceptionHandler`에 `MissingServletRequestParameterException`/`ServletRequestBindingException` 핸들러를 추가해 400 `INVALID_REQUEST`로 매핑하거나, `ResponseEntityExceptionHandler`를 상속해 프레임워크 표준 매핑을 살린다.
- 비고: 확실히 재현 가능한 회귀 테스트를 남기면 현재 동작(500)을 고정해 버리므로, 테스트는 작성하지 않고 기록만 한다.

### 10. `from`/`to`의 오프셋을 무시하고 `toLocalDate()`를 한다
- 증상: 컨트롤러가 `OffsetDateTime`을 받아 그대로 `.toLocalDate()`를 호출한다. 즉 클라이언트가 보낸 오프셋 기준 날짜가 그대로 쓰이는데, 저장된 `relationships.date`는 KST 기준 날짜다. `2026-07-01T00:00:00Z`(=KST 09:00)를 보내면 KST 기준 7/1이 맞지만, `2026-07-01T23:00:00Z`(=KST 7/2 08:00)를 보내면 7/1로 해석되어 하루가 밀린다.
- 재현 조건: UTC 오프셋으로 기간 경계를 지정해 호출.
- 근거 코드 위치: `people/controller/PeopleController.java:69`
- 심각도: medium
- 제안: `KstTimes`처럼 KST로 변환한 뒤 `toLocalDate()`를 하거나, 애초에 파라미터 타입을 `LocalDate`로 받는다.

### 11. `from > to`를 검증하지 않는다
- 증상: 뒤집힌 기간을 주면 에러 없이 빈 시계열 + `currentIntimacy`만 내려간다. 클라이언트가 버그를 알아채기 어렵다.
- 재현 조건: `from=2026-07-31T00:00:00Z&to=2026-07-01T00:00:00Z`
- 근거 코드 위치: `people/service/PeopleService.java:215-226`
- 심각도: low
- 제안: `from.isAfter(to)`면 `INVALID_REQUEST`로 거절한다.

### 12. WEEK 버킷의 대표값이 "그 주의 첫 기록"이다
- 증상: 주 단위 집계에서 각 버킷의 **첫** 기록 친밀도를 쓴다. 시계열 그래프 관점에서는 보통 주의 마지막(가장 최신) 값이나 평균을 쓰므로, 주중에 친밀도가 오르내리면 그래프가 실제 추세보다 뒤처져 보인다.
- 재현 조건: 같은 주에 친밀도 10 → 50 기록이 있을 때 `resolution=WEEK`로 조회 → 10이 내려온다.
- 근거 코드 위치: `people/service/PeopleService.java:236-240`
- 심각도: low
- 제안: 버킷 대표값 정책(마지막 값/평균)을 명시적으로 정하고 주석으로 남긴다.

### 13. 관계 존재 검증이 계산 뒤에 있다
- 증상: 관계가 아예 없어 `RELATIONSHIP_NOT_FOUND`로 끝날 요청도 시계열 버킷팅·다운샘플링을 모두 수행한 뒤에 예외를 던진다.
- 근거 코드 위치: `people/service/PeopleService.java:219-223`
- 심각도: low
- 제안: `findLatest...`를 먼저 호출해 조기 실패시킨다.

---

## GET /api/v1/people/{userId}/events

### 14. 관계 이력 전체를 기간 제한 없이 로딩한다
- 증상: 페이지에 담을 날짜는 `limit`개인데, 친밀도 변화량·관계 변화를 계산하려고 `findAllByUserIdAndPartnerUserIdOrderByDateAsc`로 **해당 상대와의 전체 관계 이력**을 매 요청마다 메모리에 올린다. 관계가 오래될수록 요청 비용이 선형으로 커진다.
- 재현 조건: 관계 기록이 수백 일치 쌓인 상대의 이벤트 목록 조회.
- 근거 코드 위치: `people/service/PeopleService.java:296`
- 심각도: medium
- 제안: 페이지 날짜 범위(+직전 1건)만 조회하도록 쿼리를 좁힌다.

### 15. `scenesByDate.get(date).get(0)`이 NPE가 될 수 있다
- 증상: 날짜 목록 쿼리와 씬 목록 쿼리가 별개의 쿼리라, 두 쿼리 사이에 해당 날짜의 씬이 삭제되면 `scenesByDate.get(date)`가 null이 되어 NPE → 500.
- 재현 조건: 두 쿼리 사이에 씬 삭제(경쟁 조건). 트랜잭션 경계가 없어 실제로 발생 가능하다(6번 참고).
- 근거 코드 위치: `people/service/PeopleService.java:311`
- 심각도: low
- 제안: `getOrDefault(date, List.of())` + 빈 경우 스킵, 또는 조회 메서드를 `@Transactional(readOnly = true)`로 묶는다.

### 16. `lines` JSON이 손상되면 500이 난다
- 증상: `preview()`가 `objectMapper.readValue`를 예외 처리 없이 호출한다. `scenes.lines`에 파싱 불가한 JSON이 들어 있으면 목록 전체가 500이 된다.
- 재현 조건: `scenes.lines`에 스키마에 맞지 않는 JSON이 저장된 경우.
- 근거 코드 위치: `people/service/PeopleService.java:343-350`
- 심각도: low
- 제안: 파싱 실패 시 `preview`를 null로 떨어뜨리고 경고 로그만 남긴다(목록 전체를 죽이지 않는다).

---

## GET /api/v1/people/{userId}/events/{date}

### 17. 상대 존재 여부를 검증하지 않아 아무 userId나 200을 받는다
- 증상: `events`는 `USER_NOT_FOUND`(404)를 던지는데, `event`는 상대 유저 조회 자체를 하지 않는다. 존재하지 않는 `userId`나 나와 아무 관계 없는 `userId`로 호출해도 `scenes: []`, `version: null`인 200이 내려간다. 같은 리소스 계열인데 404 규약이 서로 다르다.
- 재현 조건: `GET /api/v1/people/99999999/event/2026-07-20`
- 근거 코드 위치: `people/service/PeopleService.java:352-375` (컨트롤러에도 404 문서가 없다 — `people/controller/PeopleController.java:81-86`)
- 심각도: medium
- 제안: `events`와 동일하게 상대 유저를 먼저 검증하고 `USER_NOT_FOUND`를 던진다.

### 18. 그날의 내 씬을 전부 로드한 뒤 메모리에서 상대로 필터링한다
- 증상: `findAllByUserIdAndDate`로 그날 내 씬 전체 + 그 씬들의 파트너 전체를 읽고 나서 자바 스트림으로 상대만 걸러낸다. 하루 씬이 많아질수록 불필요한 로딩이 커진다. `speakerUserIds`도 관련 없는 파트너까지 포함해 `findAllById`를 돈다.
- 근거 코드 위치: `people/service/PeopleService.java:353-366`
- 심각도: low
- 제안: `SceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn`처럼 조인 조건을 쿼리로 내린다.

### 19. `version`을 필터된 첫 씬의 값으로만 결정한다
- 증상: 같은 날짜 씬들의 `version`이 서로 다를 수 있는데(스키마상 씬별 컬럼), 응답의 `version`은 첫 씬 값 하나만 쓴다. 클라이언트가 캐시 무효화 키로 쓰면 오탐/미탐이 생긴다.
- 근거 코드 위치: `people/service/PeopleService.java:372`
- 심각도: low
- 제안: 날짜 단위 version을 별도로 관리하거나, 씬별 version을 그대로 내려준다.

---

## GET /api/v1/people/{userId}/learned-facts

### 20. 발견된 이슈 없음
- 최신 관계 기록의 `partnerModel`을 그대로 반환하고, 없으면 `RELATIONSHIP_NOT_FOUND`(404)를 던진다. 컨트롤러의 `@ApiResponse` 문서와도 일치한다.
