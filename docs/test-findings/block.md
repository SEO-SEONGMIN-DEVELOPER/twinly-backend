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

### 1. `blockList`가 N+1은 아니지만, 차단이 없을 때도 `findAllById([])`를 호출한다

- **증상**: `BlockService.blockList`는 차단 목록이 비어 있어도 `userRepository.findAllById(List.of())`를 호출한다.
  Spring Data JPA는 빈 컬렉션이면 쿼리를 생략하므로 실제 부하는 없지만, 의도가 코드에 드러나지 않는다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/block/service/BlockService.java:52`
- **심각도**: info (동작 결함 아님)
- **제안**: 조기 반환으로 의도를 명시하면 읽기 쉬워진다. 지금 당장 고칠 필요는 없다.

### 2. `unblock`은 존재 여부와 무관하게 항상 200이다 (의도된 멱등)

- **증상**: 차단 이력이 없어도 `DELETE`가 200을 반환한다. 이는 결함이 아니라 멱등 설계로 판단되며,
  통합 테스트로 그 계약을 고정해 두었다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/block/service/BlockService.java:38`
- **심각도**: info
