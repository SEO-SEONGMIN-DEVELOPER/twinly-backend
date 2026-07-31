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

## DELETE /api/v1/people/{userId}/favorite

### 1. 발견된 이슈 없음
- 선호도 행이 없으면 아무것도 하지 않는 멱등 동작이며, 의도에 맞는다.

---

## GET /api/v1/people/{userId}/intimacy-series

## GET /api/v1/people/{userId}/events

## GET /api/v1/people/{userId}/events/{date}

### 2. 그날의 내 씬을 전부 로드한 뒤 메모리에서 상대로 필터링한다
- 증상: `findAllByUserIdAndDate`로 그날 내 씬 전체 + 그 씬들의 파트너 전체를 읽고 나서 자바 스트림으로 상대만 걸러낸다. 하루 씬이 많아질수록 불필요한 로딩이 커진다. `speakerUserIds`도 관련 없는 파트너까지 포함해 `findAllById`를 돈다.
- 근거 코드 위치: `people/service/PeopleService.java:353-366`
- 심각도: low
- 제안: `SceneRepository.findAllByUserIdAndWithPartnerUserIdAndDateIn`처럼 조인 조건을 쿼리로 내린다.

---

## GET /api/v1/people/{userId}/learned-facts

### 3. 발견된 이슈 없음
- 최신 관계 기록의 `partnerModel`을 그대로 반환하고, 없으면 `RELATIONSHIP_NOT_FOUND`(404)를 던진다. 컨트롤러의 `@ApiResponse` 문서와도 일치한다.
