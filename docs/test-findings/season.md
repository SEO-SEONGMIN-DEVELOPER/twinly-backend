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

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
