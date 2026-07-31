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

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
