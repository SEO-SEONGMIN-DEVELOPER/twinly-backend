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

## DELETE /api/v1/push/tokens/{deviceId}

## 참고: 문제 없다고 확인한 지점

- `@CurrentUser` 리졸버가 파라미터 선언 순서상 `@RequestBody`보다 먼저 해석되므로, 인증 실패 시 본문 검증보다 먼저 401이 나간다(의도대로 동작).
- `revoke`는 `findByUserIdAndDeviceId`로 조회하므로 다른 유저의 기기 토큰을 지울 수 없다.
- `devices.user_id` FK는 `@CurrentUser` 리졸버가 이미 DB에서 유저를 조회해 검증하므로 위반 가능성이 없다.
- 탈퇴 유저는 `UserService.resolveByAccessToken`에서 `WITHDRAWN_USER`(401)로 차단된다.
- `revokeToken()`은 행을 지우지 않고 `push_token`만 `NULL`로 비우므로, 재로그인 시 같은 행이 재사용된다(의도대로 동작).

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
