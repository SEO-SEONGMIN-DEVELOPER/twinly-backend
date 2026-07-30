# report 도메인 테스트 발견사항

대상 엔드포인트 (springdoc 스펙 기준 2개)

- `POST /api/v1/reports/users`
- `POST /api/v1/reports/ai-utterances`

작성한 테스트

- `backend/src/test/java/com/nidus/twinly/report/controller/ReportControllerUnitTest.java` (8건)
- `backend/src/test/java/com/nidus/twinly/report/service/ReportServiceUnitTest.java` (6건)
- `backend/src/test/java/com/nidus/twinly/report/integration/ReportIntegrationTest.java` (3건)

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 | **14/14 통과** |
| 통합 | **3/3 통과** |

> 운영 코드는 수정하지 않았습니다. 아래는 기록만 한 발견사항입니다.

---

## 발견사항

### 1. `AUTO_BLOCK`이 컴파일 타임 상수라 운영 중 끌 수 없다

- **증상**: 신고 시 자동 차단 여부가 `ReportService`의 상수로 고정되어 있다. 응답 `isBlocked`도 이 상수를
  그대로 반환하므로, 정책 변경 시 재배포가 필요하다. 또한 `if (AUTO_BLOCK && ...)`은 상수가 `true`인 한
  **`false` 분기가 실행될 수 없어** 그 경로는 사실상 죽은 코드다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/report/service/ReportService.java:39`
- **심각도**: low
- **제안**: 지금 정책이 바뀔 예정이 없다면 상수로 두는 편이 단순하다. 다만 응답 필드로 노출되어 있으므로,
  값이 항상 고정이라면 필드로 내릴 이유가 있는지 재검토할 여지가 있다.

### 2. `aiUtterance`는 신고 대상 유저의 존재를 검증하지 않는다

- **증상**: `targetUserId`가 실재하는지 확인하지 않고, `scene.getUserId()`와 일치하는지만 본다.
  장면 소유자가 곧 대상이므로 실질적으로는 정합성이 유지되지만, `targetUserId`를 클라이언트가
  보내야 할 이유가 사라진다. 서버가 `scene.getUserId()`로 결정하면 되는 값을 입력으로 받고 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/report/service/ReportService.java:52`
- **심각도**: info
- **제안**: 요청에서 `targetUserId`를 제거하고 서버가 장면에서 유도하면, `SCENE_TARGET_MISMATCH` 분기
  자체가 필요 없어진다. 지금 당장 바꿀 필요는 없고 API 정리 시 후보로 둘 만하다.
