# me 도메인 테스트 발견사항

담당 엔드포인트(20개)의 컨트롤러 슬라이스 / 서비스 단위 / 통합 테스트를 작성하면서 확인한 **미해결** 항목이다.
해결된 항목은 이 문서에서 제거하고 회귀 테스트로 대체한다. 이력은 [_summary.md](_summary.md) 참조.

---

## POST /api/v1/me/restore

### 1. 복구 시 `withdrawalScheduledAt`이 초기화되지 않아 `GET /api/v1/me/status` 응답이 오염된다

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

## POST /api/v1/me/consents

### 2. 아직 발효되지 않은(또는 `effectiveAt`이 null인) 정책 버전에도 동의할 수 있다

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

### 3. 존재하지 않는 정책 버전을 조용히 무시한다 (동의 API와 비일관)

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

### 4. `limit` 상한이 없다

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

### 5. `affiliation`이 `@NotNull`만 걸려 있어 공백 문자열이 통과한다

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

### 6. "조회 후 없으면 저장" 패턴이라 동시 요청 시 유니크 제약 위반이 발생할 수 있다

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

### 7. DELETE 요청에 바디를 요구한다

- **증상**
  철회 대상 목록을 `@RequestBody`로 받는다. RFC 상 DELETE의 바디는 정의되지 않아
  일부 HTTP 클라이언트/프록시/CDN이 바디를 제거하며, 그 경우 `HttpMessageNotReadableException` → 400이 된다.

- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/me/controller/MeController.java:116` — `@DeleteMapping("/api/v1/me/consents")` + `@Valid @RequestBody`

- **심각도**: low

- **제안**
  `POST /api/v1/me/consents/revoke` 같은 별도 엔드포인트로 옮기거나, 쿼리 파라미터로 받는다.
  현행 유지 시 클라이언트 제약을 API 스펙에 명시한다.
