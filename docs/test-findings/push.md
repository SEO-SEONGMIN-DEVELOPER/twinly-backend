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

### 1. 동시 등록 요청이 UNIQUE 제약을 위반해 500으로 떨어질 수 있다

- **증상**: 신규 `deviceId`에 대해 "조회 → 없으면 insert"의 check-then-act 구조라, 같은 `deviceId`로 두 요청이 동시에 들어오면 둘 다 `findByDeviceId`가 empty를 받고 둘 다 `save`한다. 뒤늦은 쪽이 `uk_devices_device_id`를 위반해 `DataIntegrityViolationException`이 나고, 전용 핸들러가 없어 `handleUnexpected`가 잡아 **500 INTERNAL_ERROR**로 응답한다. (클라이언트 잘못이 아닌데 5xx로 나가고 에러 로그도 남는다.)
- **재현 조건**: 앱 기동 직후 등록 요청이 중복 발행되거나 네트워크 타임아웃 후 재시도가 겹치는 경우.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/push/service/PushService.java:19` ~ `:23`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:238` (`uk_devices_device_id`)
  - `backend/src/main/java/com/nidus/twinly/common/web/GlobalExceptionHandler.java:61` (`handleUnexpected` → 500)
- **심각도**: low (경합 창이 좁고 클라이언트 재시도로 복구 가능하나, 5xx 알람을 오염시킨다)
- **제안**: `DataIntegrityViolationException`을 잡아 재조회 후 `reregister`로 폴백하거나, `INSERT ... ON DUPLICATE KEY UPDATE`로 원자화한다. 지금 당장은 트래픽이 없으니 관측되면 대응해도 된다.

## DELETE /api/v1/push/tokens/{deviceId}

## 참고: 문제 없다고 확인한 지점

- `@CurrentUser` 리졸버가 파라미터 선언 순서상 `@RequestBody`보다 먼저 해석되므로, 인증 실패 시 본문 검증보다 먼저 401이 나간다(의도대로 동작).
- `revoke`는 `findByUserIdAndDeviceId`로 조회하므로 다른 유저의 기기 토큰을 지울 수 없다.
- `devices.user_id` FK는 `@CurrentUser` 리졸버가 이미 DB에서 유저를 조회해 검증하므로 위반 가능성이 없다.
- 탈퇴 유저는 `UserService.resolveByAccessToken`에서 `WITHDRAWN_USER`(401)로 차단된다.
- `revokeToken()`은 행을 지우지 않고 `push_token`만 `NULL`로 비우므로, 재로그인 시 같은 행이 재사용된다(의도대로 동작).
