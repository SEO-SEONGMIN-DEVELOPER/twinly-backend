# block 도메인 테스트 발견사항

대상 엔드포인트 (springdoc 스펙 기준 3개)

- `PUT /api/v1/blocks/{userId}`
- `DELETE /api/v1/blocks/{userId}`
- `GET /api/v1/blocks`

작성한 테스트

- `backend/src/test/java/com/nidus/twinly/block/controller/BlockControllerUnitTest.java` (5건)
- `backend/src/test/java/com/nidus/twinly/block/service/BlockServiceUnitTest.java` (5건)
- `backend/src/test/java/com/nidus/twinly/block/integration/BlockIntegrationTest.java` (7건)

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 | **10/10 통과** |
| 통합 | **7/7 통과** |

통합 테스트 케이스

| 케이스 | 결과 |
|---|---|
| 차단 성공 → `blocks` 행 생성 | 통과 |
| 자기 자신 차단 → 422 `CANNOT_BLOCK_SELF`, 행 미생성 | 통과 |
| 차단 해제 → 행 삭제 | 통과 |
| 차단 이력 없는 대상 해제 → 200 (멱등) | 통과 |
| 목록 조회 → id·이름 응답 | 통과 |
| 목록 조회 시 탈퇴 유저 → `탈퇴한 사용자`로 마스킹 | 통과 |
| 인증 헤더 없음 → 401 | 통과 |

> 운영 코드는 수정하지 않았습니다. 아래는 기록만 한 발견사항입니다.

---

## 발견사항

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
