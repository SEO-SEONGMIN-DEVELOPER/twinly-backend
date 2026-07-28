# me 도메인 테스트 발견사항

담당 엔드포인트(20개)의 컨트롤러 슬라이스 / 서비스 단위 / 통합 테스트를 작성하면서 확인한 운영 코드 이슈입니다.
운영 코드는 수정하지 않았고, 기록만 남깁니다.

---

# [이번 라운드] springdoc 스펙 기준 통합 커버리지 확장

통합 테스트를 5건 → 19건으로 늘려 me의 20개 오퍼레이션을 전부 관통시켰다.

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`MeControllerUnitTest` 34 + `MeServiceUnitTest` 46) | **80/80 통과** |
| 통합 (`MeIntegrationTest`) | **18/19 통과, 1 실패** |

| 실패 테스트 | 원인 |
|---|---|
| `프로필 사진 commit: 업로드가 끝난 key면 photos 행이 생성된다` | BUG-PHOTO-01 |

## BUG-PHOTO-01 — `POST /api/v1/me/profile/photo/commit`이 **항상 500**이다

- **심각도**: **critical**
- **증상**: 사진이 없는 유저가 커밋하면 INSERT가 SQL 문법 오류로 깨져 500.
- **원인**: `Photo.key` 필드가 매핑되는 컬럼명 `key`는 **MySQL 예약어**다. DDL은 백틱으로 감쌌지만
  (`V1__init_schema.sql:343`) 엔티티에 `@Column(name = "`key`")`가 없어 Hibernate가 INSERT에
  인용부호 없이 `key`를 넣는다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/user/entity/Photo.java:30`
- **onboarding 도메인의 `AnonSessionPhoto`도 동일한 결함**이다. 상세 분석·수정 제안은
  [onboarding.md](onboarding.md)의 BUG-PHOTO-01 항목에 정리해 두었다.

### 기존에 기록된 "사진 교체가 반영되지 않는다" 건과의 관계

이 문서 아래쪽에 이미 기록된 `@Transactional` 누락 건과 **별개이면서 서로를 가리는** 관계다.

- 최초 등록 경로(`save`) → BUG-PHOTO-01로 **500**
- 교체 경로(dirty checking) → `@Transactional` 누락으로 **조용히 미반영**

즉 최초 등록이 막혀 있어 교체 경로에 도달할 수조차 없다. 둘 다 고쳐야 이 엔드포인트가 동작한다.

## 이번 라운드에 추가한 통합 테스트 (모두 통과)

| 오퍼레이션 | 검증 내용 |
|---|---|
| `POST /me/restore` | 탈퇴 신청 취소 후 DB `withdrawalRequestedAt` 비워짐 + 상태 조회 `isDeleted=false` / 신청 이력 없어도 200 (멱등) |
| `GET /me/profile-edit-view` | 암호화 컬럼이 복호화되어 응답, 사진 없으면 `profilePhoto` 미포함 |
| `PATCH /me/profile` | 소속 평문 + 블라인드 인덱스 해시가 함께 갱신 |
| `DELETE /me/consents` | 선택 정책 철회 → 조회 `isGranted=false` / 필수 정책은 403 `REQUIRED_POLICY_REVOKE_DENIED` |
| `GET`·`PATCH /me/profile/visibility-settings/{type}` | 공개 설정 on/off 왕복이 DB 공개 동의 행과 응답에 함께 반영 |
| `POST /me/app-notifications/read-all` | `lastAppNotificationId` 이하 전부 읽음 처리 → 미읽음 0 |
| `GET /me/hesitations` | 오늘·미답변 필터가 실제 쿼리로 동작 |
| `POST /me/hesitations/{id}/answer` | 정상 답변 저장 / 남의 질문 403 / 선택지 밖 답 422 |
| `POST /me/profile/photo/presign` | key가 `profile/{userId}/` 접두사로 생성 |

---

## POST /api/v1/me/profile/photo/commit

### 1. `profilePhotoCommit`에 `@Transactional`이 없어 "기존 사진 갱신"이 DB에 반영되지 않는다

- **증상**
  이미 프로필 사진이 등록된 유저가 사진을 다시 커밋하면 HTTP 200과 새 `photoUrl`이 정상적으로 내려오지만,
  `photos` 테이블의 `key` / `x_pos` / `y_pos` / `width` / `height` / `uploaded_at`은 예전 값 그대로 남는다.
  (사진이 처음 등록되는 경우는 `photoRepository.save(...)`를 타므로 정상 동작한다 → "최초 등록은 되는데 교체만 안 되는" 형태로 드러난다.)

- **재현 조건**
  1. 유저 A가 프로필 사진을 한 번 commit 해서 `photos` 행을 만든다.
  2. 새 key로 다시 `POST /api/v1/me/profile/photo/commit` 호출.
  3. 응답은 200 + 새 URL이지만, `photos` 행을 조회하면 이전 key/좌표 그대로.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:123` — `profilePhotoCommit`에 `@Transactional`이 없음
    (같은 클래스의 `withdraw`(138), `profile`(171), `restore`(179) 등 다른 변경 메서드에는 붙어 있다)
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:129` — `photo.changePhoto(...)`로 더티 체킹에 의존
  - `backend/src/main/resources/application.yaml:22` — `spring.jpa.open-in-view: false`
  트랜잭션이 없으면 `photoRepository.findByUserIdAndType(...)` 호출이 자체 트랜잭션(`SimpleJpaRepository`의 `@Transactional(readOnly = true)`)으로 끝나면서
  영속성 컨텍스트가 닫히고, 반환된 `Photo`는 **준영속(detached)** 상태가 된다. 이후 세터 호출은 UPDATE로 이어지지 않는다.

- **심각도**: high

- **제안**
  `profilePhotoCommit`에 `@Transactional`을 붙인다. 외부 호출(S3 존재 확인/CloudFront 서명)이 트랜잭션 안으로 들어오는 게 부담이라면,
  외부 호출을 먼저 끝낸 뒤 DB 반영 구간만 별도 트랜잭션 메서드로 분리하는 방식도 가능하다.

---

## POST /api/v1/me/restore

### 2. 복구 시 `withdrawalScheduledAt`이 초기화되지 않아 `GET /api/v1/me/status` 응답이 오염된다

- **증상**
  탈퇴 신청 → 복구를 마친 유저가 `GET /api/v1/me/status`를 호출하면
  `withdrawal.isDeleted`는 `false`인데 `withdrawal.recoverableUntil`에는 **이전 탈퇴 신청 기준의 복구 마감 시각**이 그대로 내려온다.
  클라이언트가 `recoverableUntil`만 보고 "복구 가능 상태"로 판단하면 잘못된 화면이 뜬다.

- **재현 조건**
  1. `DELETE /api/v1/me` (탈퇴 신청)
  2. `POST /api/v1/me/restore` (복구)
  3. `GET /api/v1/me/status` → `{"withdrawal":{"isDeleted":false,"recoverableUntil":"<탈퇴 신청 + 15일>"}}`

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/user/entity/User.java:135` — `cancelWithdrawal()`이 `withdrawalRequestedAt`만 `null`로 만들고 `withdrawalScheduledAt`은 남겨둔다
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:374` — `status()`가 `isDeleted`는 `withdrawalRequestedAt` 기준, `recoverableUntil`은 `withdrawalScheduledAt` 기준으로 각각 따로 계산

- **심각도**: medium

- **제안**
  `cancelWithdrawal()`에서 `withdrawalScheduledAt`도 함께 `null`로 만든다.
  (또는 `status()`에서 `isDeleted`가 false면 `recoverableUntil`을 `null`로 내려보내 두 필드의 정합성을 응답 계층에서 보장한다.)

---

## GET /api/v1/me/hesitations

### 3. 필수 쿼리 파라미터가 빠지면 400이 아니라 500이 내려간다

- **증상**
  `duration` 또는 `status` 없이 호출하면 `MissingServletRequestParameterException`이 발생하는데,
  `GlobalExceptionHandler`에 이 예외(또는 상위 `ServletRequestBindingException`)에 대한 핸들러가 없어
  마지막 catch-all인 `@ExceptionHandler(Exception.class)`가 잡아 **500 INTERNAL_ERROR**로 응답한다.
  클라이언트 입력 오류가 서버 오류로 보고되고, 에러 로그도 `[500 Error]`로 남아 노이즈가 된다.

- **재현 조건**
  `GET /api/v1/me/hesitations?status=ALL` (duration 누락) 또는 `GET /api/v1/me/hesitations` 호출.
  ※ 값이 잘못된 경우(`duration=WEEK`)는 `MethodArgumentTypeMismatchException` 핸들러가 있어 정상적으로 400이 나간다 — **누락일 때만** 500이다.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/controller/MeController.java:180` — `@RequestParam HesitationDuration duration` (required 기본값 true)
  - `backend/src/main/java/com/nidus/twinly/me/controller/MeController.java:181` — `@RequestParam HesitationStatus status`
  - `backend/src/main/java/com/nidus/twinly/common/web/GlobalExceptionHandler.java:45` — 타입 불일치만 400으로 처리
  - `backend/src/main/java/com/nidus/twinly/common/web/GlobalExceptionHandler.java:61` — 나머지는 전부 500

- **심각도**: medium
  (me 도메인 전용 문제가 아니라 `@RequestParam`/`@RequestPart`를 쓰는 모든 API에 공통으로 적용된다.)

- **제안**
  `GlobalExceptionHandler`에 `@ExceptionHandler(ServletRequestBindingException.class)`(또는 `MissingServletRequestParameterException`)를 추가해
  `INVALID_REQUEST` + 400으로 매핑한다.
  ※ 버그를 테스트로 고정하지 않기 위해 이 케이스는 테스트로 작성하지 않았다. 수정 후 400 검증 테스트를 추가하는 것을 권장한다.

---

## POST /api/v1/me/consents

### 4. 아직 발효되지 않은(또는 `effectiveAt`이 null인) 정책 버전에도 동의할 수 있다

- **증상**
  `GET /api/v1/me/consents`는 `effectiveAt`이 현재보다 과거인 버전만 노출하는데,
  `POST /api/v1/me/consents`는 `PolicyCatalog`가 반환하는 **모든 버전**을 그대로 받아들인다.
  즉 조회 API에 노출되지 않는 미래 버전(또는 `effectiveAt`이 null인 초안)의 `version`을 직접 넣으면 동의 기록이 생성된다.

- **재현 조건**
  `policies`에 `effective_at`이 미래이거나 null인 v3 행을 만들어 두고
  `POST /api/v1/me/consents` 바디에 `{"grants":[{"policyId":"terms_of_service","version":"3"}]}` 전송 → 200, `agreements` 행 생성.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:200` — 조회 경로는 `effectiveAt` 필터링 수행
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:235` — 동의 경로는 `policyCatalog.loadByKey(...)` 결과를 그대로 사용
  - `backend/src/main/java/com/nidus/twinly/legal/service/PolicyCatalog.java:22` — `effectiveAt` 조건 없이 버전 전체를 로드

- **심각도**: medium

- **제안**
  `PolicyCatalog.loadByKey`에 "발효된 버전만" 조건을 넣거나, `grantConsents`에서 `effectiveAt` 검증 후 `POLICY_NOT_FOUND`를 던진다.

---

## DELETE /api/v1/me/consents

### 5. 존재하지 않는 정책 버전을 조용히 무시한다 (동의 API와 비일관)

- **증상**
  `grantConsents`는 카탈로그에 없는 `(policyId, version)`이면 `POLICY_NOT_FOUND`(404)를 던지는데,
  `revokeConsents`는 `filter(policy -> policy != null)`로 걸러내고 200을 반환한다.
  오타나 잘못된 버전을 보낸 클라이언트가 "철회 성공"으로 오인한다.

- **재현 조건**
  `DELETE /api/v1/me/consents` 바디에 `{"grants":[{"policyId":"marketing","version":"99"}]}` 전송 → 200, 아무것도 철회되지 않음.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:265` — `.filter(policy -> policy != null)`
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:246` — 동의 경로는 동일 상황에서 예외

- **심각도**: low

- **제안**
  두 API의 정책을 통일한다. 철회는 멱등성이 중요하니 "무시"를 유지하려면 그 의도를 주석/스펙에 명시하고,
  아니면 동의와 동일하게 `POLICY_NOT_FOUND`를 던진다.

---

## GET /api/v1/me/app-notifications/feeds

### 6. `limit` 상한이 없다

- **증상**
  `limit`은 "0 이하이거나 null이면 기본값 20"만 처리하고 상한은 없다.
  `?limit=1000000`처럼 큰 값을 보내면 그대로 `LIMIT 1000000`이 나가 응답 크기/메모리가 클라이언트 입력에 좌우된다.

- **재현 조건**
  `GET /api/v1/me/app-notifications/feeds?limit=1000000`

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:324` — `int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_APP_NOTIFICATIONS_LIMIT;`

- **심각도**: low

- **제안**
  `Math.min(limit, MAX_APP_NOTIFICATIONS_LIMIT)` 형태로 상한(예: 100)을 둔다.

---

## PATCH /api/v1/me/profile

### 7. `affiliation`이 `@NotNull`만 걸려 있어 공백 문자열이 통과한다

- **증상**
  `{"affiliation":"   "}` 또는 `{"affiliation":""}`이 400 없이 통과해 소속이 공백으로 저장된다.
  (블라인드 인덱스 해시도 공백 기준으로 만들어져 검색 인덱스가 오염된다.)

- **재현 조건**
  `PATCH /api/v1/me/profile` 바디 `{"affiliation":""}` → 200, `users.affiliation`이 빈 문자열로 갱신.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/dto/request/MeProfileRequest.java:6` — `@NotNull String affiliation`
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:176` — 별도 검증 없이 `changeAffiliation`

- **심각도**: low

- **제안**
  `@NotBlank` + 길이 상한(`@Size`)으로 바꾼다.

---

## PATCH /api/v1/me/push-notifications/{type}, PATCH /api/v1/me/profile/visibility-settings/{type}

### 8. "조회 후 없으면 저장" 패턴이라 동시 요청 시 유니크 제약 위반이 발생할 수 있다

- **증상**
  같은 유저가 동일 설정 변경을 동시에 두 번 보내면 두 요청 모두 "행 없음"으로 판단하고 INSERT를 시도해
  유니크 제약 위반(`DataIntegrityViolationException`)이 발생, 한쪽이 500으로 실패한다.

- **재현 조건**
  동일 유저 토큰으로 `PATCH /api/v1/me/push-notifications/CHAT`을 동시에 2건 호출 (설정 행이 없는 상태).

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:293` — `findByUserIdAndChannelAndType(...).ifPresentOrElse(..., save)`
  - `backend/src/main/java/com/nidus/twinly/me/service/MeService.java:314` — `existsByUserIdAndField(...)` 후 `save`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:308` — `uk_notification_settings_user_id_channel_id_type`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:252` — `uk_disclosure_agreements_user_id_field`

- **심각도**: low (사용자가 실제로 동시에 토글할 확률은 낮음)

- **제안**
  `INSERT ... ON DUPLICATE KEY UPDATE`(upsert) 네이티브 쿼리로 바꾸거나,
  `DataIntegrityViolationException`을 잡아 재조회 후 업데이트하는 방식으로 방어한다.

---

## DELETE /api/v1/me/consents

### 9. DELETE 요청에 바디를 요구한다

- **증상**
  철회 대상 목록을 `@RequestBody`로 받는다. RFC 상 DELETE의 바디는 정의되지 않아
  일부 HTTP 클라이언트/프록시/CDN이 바디를 제거하며, 그 경우 `HttpMessageNotReadableException` → 400이 된다.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/controller/MeController.java:116` — `@DeleteMapping("/api/v1/me/consents")` + `@Valid @RequestBody`

- **심각도**: low

- **제안**
  `POST /api/v1/me/consents/revoke` 같은 별도 엔드포인트로 옮기거나, 쿼리 파라미터로 받는다.
  현행 유지 시 클라이언트 제약을 API 스펙에 명시한다.

---

## 참고: 테스트로 고정하지 않은 사항

- 위 **1번(사진 커밋 갱신 누락)** 은 서비스 단위 테스트(목 기반)에서는 드러나지 않는다.
  `MeServiceUnitTest#profilePhotoCommit_updates_existing_photo`는 "엔티티 상태가 바뀌고 save를 다시 호출하지 않는다"까지만 검증한다.
  실제 DB 반영 여부는 `@Transactional` 부착 후 통합 테스트로 검증하는 것을 권장한다.
- 위 **3번(필수 파라미터 누락 시 500)** 은 잘못된 동작을 테스트로 굳히지 않기 위해 의도적으로 테스트를 작성하지 않았다.
