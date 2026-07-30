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

## PATCH /api/v1/me/push-notifications/{type}, PATCH /api/v1/me/profile/visibility-settings/{type}

### 2. "조회 후 없으면 저장" 패턴이라 동시 요청 시 유니크 제약 위반이 발생할 수 있다

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
