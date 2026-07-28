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

### 2. 차단 API가 대상 유저의 존재 여부를 검증하지 않는다

- **증상**: 존재하지 않는 `userId`로 `PUT /api/v1/blocks/{userId}`를 호출해도 200이 반환되고
  `blocks` 행이 그대로 생성된다. `blocks` 테이블에 `blocked_user_id` FK가 없어 DB도 막지 않는다.
  차단 목록 조회 시에는 해당 유저를 못 찾아 `탈퇴한 사용자`로 표시되므로, **없는 유저와 탈퇴한 유저가 구분되지 않는다**.
- **재현 조건**: 인증된 유저가 임의의 존재하지 않는 id로 차단 API 호출 → 200 + 행 생성.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/block/service/BlockService.java:26` (존재 검증 없음)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql` (`blocks`에 `blocked_user_id` FK 부재)
- **심각도**: low
- **제안**: 차단 자체가 상대에게 알려지지 않는 기능이라 실질 피해는 작다. 다만 "없는 유저"를 404로 끊으려면
  `userRepository.existsById(blockedUserId)` 검증을 추가하는 편이 목록 응답의 의미도 명확해진다.

### 3. `unblock`은 존재 여부와 무관하게 항상 200이다 (의도된 멱등)

- **증상**: 차단 이력이 없어도 `DELETE`가 200을 반환한다. 이는 결함이 아니라 멱등 설계로 판단되며,
  통합 테스트로 그 계약을 고정해 두었다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/block/service/BlockService.java:38`
- **심각도**: info
