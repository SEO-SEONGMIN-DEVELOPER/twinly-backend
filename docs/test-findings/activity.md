# activity 도메인 테스트 발견사항

대상 엔드포인트 (springdoc 스펙 기준 1개)

- `GET /api/v1/activities/{date}`

작성한 테스트

- `backend/src/test/java/com/nidus/twinly/activity/controller/ActivityControllerUnitTest.java` (3건)
- `backend/src/test/java/com/nidus/twinly/activity/service/ActivityServiceUnitTest.java` (7건)
- `backend/src/test/java/com/nidus/twinly/activity/integration/ActivityIntegrationTest.java` (3건)

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 | **10/10 통과** |
| 통합 | **3/3 통과** |

---

## 발견사항

이 도메인의 미해결 항목은 없다. 씬·JSON 파싱(B6)까지 처리 완료했다.
