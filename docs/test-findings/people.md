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

## GET /api/v1/people/{userId}/learned-facts

### 2. 발견된 이슈 없음
- 최신 관계 기록의 `partnerModel`을 그대로 반환하고, 없으면 `RELATIONSHIP_NOT_FOUND`(404)를 던진다. 컨트롤러의 `@ApiResponse` 문서와도 일치한다.

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
