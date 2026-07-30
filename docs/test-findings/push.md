# push 도메인 테스트 발견사항

담당 엔드포인트: `POST /api/v1/push/tokens`, `DELETE /api/v1/push/tokens/{deviceId}`
관련 운영 코드: `PushController`, `PushService`, `Device`, `DeviceRepository`, `PushTokenRegisterRequest`

---

## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`PushControllerUnitTest` 7 + `PushServiceUnitTest` 5) | **12/12 통과** |
| 통합 (`PushIntegrationTest`) | **3/3 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 2개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## POST /api/v1/push/tokens

### 1. 같은 fcmToken을 가진 다른 기기 행을 정리하지 않아 중복 푸시가 발생할 수 있다

- **증상**: `push_token`에 UNIQUE 제약이 없고, 등록 시 동일한 `fcmToken`을 보유한 다른 `devices` 행을 무효화하지 않는다. 같은 FCM 토큰이 두 행에 남으면 그 유저에게 발송하는 알림이 두 번 전달된다.
- **재현 조건**: 클라이언트가 `deviceId`를 새로 생성했는데(앱 재설치·로컬 저장소 초기화·기기 식별자 재발급 등) FCM 토큰은 이전 값을 유지하는 경우.
  1. `deviceId = D1`, `fcmToken = T`로 등록
  2. `deviceId = D2`, `fcmToken = T`로 등록
  3. `SELECT COUNT(*) FROM devices WHERE push_token = T` → 2
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/push/service/PushService.java:18` (`register` 전체 — 동일 토큰 보유 행 정리 없음)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:233` (`push_token TEXT` — UNIQUE 없음)
- **심각도**: medium
- **제안**: `register`에서 `deviceId`가 다른데 `push_token`이 같은 행들의 `push_token`을 `NULL`로 비운다(FCM 권고: 하나의 토큰은 하나의 기기 레코드에만 존재해야 함). 실제 발송 로직을 붙이는 시점에 함께 처리하면 충분하다.

### 2. 동시 등록 요청이 UNIQUE 제약을 위반해 500으로 떨어질 수 있다

- **증상**: 신규 `deviceId`에 대해 "조회 → 없으면 insert"의 check-then-act 구조라, 같은 `deviceId`로 두 요청이 동시에 들어오면 둘 다 `findByDeviceId`가 empty를 받고 둘 다 `save`한다. 뒤늦은 쪽이 `uk_devices_device_id`를 위반해 `DataIntegrityViolationException`이 나고, 전용 핸들러가 없어 `handleUnexpected`가 잡아 **500 INTERNAL_ERROR**로 응답한다. (클라이언트 잘못이 아닌데 5xx로 나가고 에러 로그도 남는다.)
- **재현 조건**: 앱 기동 직후 등록 요청이 중복 발행되거나 네트워크 타임아웃 후 재시도가 겹치는 경우.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/push/service/PushService.java:19` ~ `:23`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:238` (`uk_devices_device_id`)
  - `backend/src/main/java/com/nidus/twinly/common/web/GlobalExceptionHandler.java:61` (`handleUnexpected` → 500)
- **심각도**: low (경합 창이 좁고 클라이언트 재시도로 복구 가능하나, 5xx 알람을 오염시킨다)
- **제안**: `DataIntegrityViolationException`을 잡아 재조회 후 `reregister`로 폴백하거나, `INSERT ... ON DUPLICATE KEY UPDATE`로 원자화한다. 지금 당장은 트래픽이 없으니 관측되면 대응해도 된다.

### 3. 신규 등록과 갱신을 클라이언트가 구분할 수 없다

- **증상**: 핸들러가 `void`라 항상 200 + 빈 본문이며, 새 기기가 만들어졌는지 기존 행이 갱신됐는지 응답으로 알 수 없다. 리소스 생성 경로임에도 201이 아니다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/push/controller/PushController.java:23` ~ `:27`
- **심각도**: low
- **제안**: 클라이언트가 지금 이 구분을 필요로 하지 않는다면 현행 유지가 합리적이다(불필요한 응답 계약을 미리 만들 이유가 없음). 필요해지는 시점에 상태를 반환하도록 바꾼다.

---

## DELETE /api/v1/push/tokens/{deviceId}

## 참고: 문제 없다고 확인한 지점

- `@CurrentUser` 리졸버가 파라미터 선언 순서상 `@RequestBody`보다 먼저 해석되므로, 인증 실패 시 본문 검증보다 먼저 401이 나간다(의도대로 동작).
- `revoke`는 `findByUserIdAndDeviceId`로 조회하므로 다른 유저의 기기 토큰을 지울 수 없다.
- `devices.user_id` FK는 `@CurrentUser` 리졸버가 이미 DB에서 유저를 조회해 검증하므로 위반 가능성이 없다.
- 탈퇴 유저는 `UserService.resolveByAccessToken`에서 `WITHDRAWN_USER`(401)로 차단된다.
- `revokeToken()`은 행을 지우지 않고 `push_token`만 `NULL`로 비우므로, 재로그인 시 같은 행이 재사용된다(의도대로 동작).
