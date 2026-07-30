# people 도메인 테스트 발견사항

대상 컨트롤러: `backend/src/main/java/com/nidus/twinly/people/controller/PeopleController.java`
대상 서비스: `backend/src/main/java/com/nidus/twinly/people/service/PeopleService.java`

> 운영 코드는 수정하지 않았고, 테스트 작성 중 발견한 버그/의심 지점만 기록한다.

---

## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`PeopleControllerUnitTest` 17 + `PeopleServiceUnitTest` 24) | **41/41 통과** |
| 통합 (`PeopleIntegrationTest`) | **10/10 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 8개는 단위·통합 양쪽 모두 커버되어 있다.
탈퇴·차단 표기(B4)는 처리 완료했고, 아래는 아직 남은 항목이다.

---

## PUT /api/v1/people/{userId}/favorite

### 1. 즐겨찾기 등록의 check-then-act 경합이 500으로 샌다 (B12)
- 증상: `favorite()`는 "선호도 조회 → 없으면 생성 → 저장"이다. 동시에 두 요청이 들어오면 둘 다 "없음"으로 판단해 INSERT를 시도하고, `uk_encounter_preferences_encounter_id_user_id` 유니크 위반으로 한쪽이 500이 된다.
- 재현 조건: 같은 (encounter, user)에 대해 즐겨찾기 등록을 동시에 두 번 호출.
- 근거 코드 위치: `people/service/PeopleService.java:196-206`, 스키마 `db/migration/V1__init_schema.sql:265`
- 심각도: medium
- 제안: `DataIntegrityViolationException`을 "이미 등록됨"으로 흡수해 멱등 성공으로 처리한다. **B12(동시 요청 경합)에 묶여 있으므로 me #4·push #3·season #1과 한 번에 처리한다.**
- 비고: **트랜잭션 경계 부재는 해결됐다**(B7). `PeopleService`에 클래스 레벨 `@Transactional(readOnly = true)`, `favorite`/`deleteFavorite`에 `@Transactional`이 붙었다. 다만 트랜잭션을 걸어도 이 경합은 사라지지 않는다 — 두 트랜잭션이 각자 "없음"을 보는 것은 격리 수준의 문제이지 경계의 문제가 아니기 때문이다.

---

## DELETE /api/v1/people/{userId}/favorite

### 2. 발견된 이슈 없음
- 선호도 행이 없으면 아무것도 하지 않는 멱등 동작이며, 의도에 맞는다.

---

## GET /api/v1/people/{userId}/intimacy-series

### 3. `from`/`to`의 오프셋을 무시하고 `toLocalDate()`를 한다
- 증상: 컨트롤러가 `OffsetDateTime`을 받아 그대로 `.toLocalDate()`를 호출한다. 즉 클라이언트가 보낸 오프셋 기준 날짜가 그대로 쓰이는데, 저장된 `relationships.date`는 KST 기준 날짜다. `2026-07-01T00:00:00Z`(=KST 09:00)를 보내면 KST 기준 7/1이 맞지만, `2026-07-01T23:00:00Z`(=KST 7/2 08:00)를 보내면 7/1로 해석되어 하루가 밀린다.
- 재현 조건: UTC 오프셋으로 기간 경계를 지정해 호출.
- 근거 코드 위치: `people/controller/PeopleController.java:69`
- 심각도: medium
- 제안: `KstTimes`처럼 KST로 변환한 뒤 `toLocalDate()`를 하거나, 애초에 파라미터 타입을 `LocalDate`로 받는다.

### 4. WEEK 버킷의 대표값이 "그 주의 첫 기록"이다
- 증상: 주 단위 집계에서 각 버킷의 **첫** 기록 친밀도를 쓴다. 시계열 그래프 관점에서는 보통 주의 마지막(가장 최신) 값이나 평균을 쓰므로, 주중에 친밀도가 오르내리면 그래프가 실제 추세보다 뒤처져 보인다.
- 재현 조건: 같은 주에 친밀도 10 → 50 기록이 있을 때 `resolution=WEEK`로 조회 → 10이 내려온다.
- 근거 코드 위치: `people/service/PeopleService.java:236-240`
- 심각도: low
- 제안: 버킷 대표값 정책(마지막 값/평균)을 명시적으로 정하고 주석으로 남긴다.

### 5. 관계 존재 검증이 계산 뒤에 있다
- 증상: 관계가 아예 없어 `RELATIONSHIP_NOT_FOUND`로 끝날 요청도 시계열 버킷팅·다운샘플링을 모두 수행한 뒤에 예외를 던진다.
- 근거 코드 위치: `people/service/PeopleService.java:219-223`
- 심각도: low
- 제안: `findLatest...`를 먼저 호출해 조기 실패시킨다.

---

## GET /api/v1/people/{userId}/events

### 6. 관계 이력 전체를 기간 제한 없이 로딩한다
- 증상: 페이지에 담을 날짜는 `limit`개인데, 친밀도 변화량·관계 변화를 계산하려고 `findAllByUserIdAndPartnerUserIdOrderByDateAsc`로 **해당 상대와의 전체 관계 이력**을 매 요청마다 메모리에 올린다. 관계가 오래될수록 요청 비용이 선형으로 커진다.
- 재현 조건: 관계 기록이 수백 일치 쌓인 상대의 이벤트 목록 조회.
- 근거 코드 위치: `people/service/PeopleService.java:296`
- 심각도: medium
- 제안: 페이지 날짜 범위(+직전 1건)만 조회하도록 쿼리를 좁힌다.

---

## GET /api/v1/people/{userId}/events/{date}

### 7. 그날의 내 씬을 전부 로드한 뒤 메모리에서 상대로 필터링한다
- 증상: `findAllByUserIdAndDate`로 그날 내 씬 전체 + 그 씬들의 파트너 전체를 읽고 나서 자바 스트림으로 상대만 걸러낸다. 하루 씬이 많아질수록 불필요한 로딩이 커진다. `speakerUserIds`도 관련 없는 파트너까지 포함해 `findAllById`를 돈다.
- 근거 코드 위치: `people/service/PeopleService.java:353-366`
- 심각도: low
- 제안: `SceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn`처럼 조인 조건을 쿼리로 내린다.

---

## GET /api/v1/people/{userId}/learned-facts

### 8. 발견된 이슈 없음
- 최신 관계 기록의 `partnerModel`을 그대로 반환하고, 없으면 `RELATIONSHIP_NOT_FOUND`(404)를 던진다. 컨트롤러의 `@ApiResponse` 문서와도 일치한다.
