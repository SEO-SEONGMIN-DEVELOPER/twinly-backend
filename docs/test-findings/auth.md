# auth 도메인 테스트 발견사항

담당 엔드포인트
- POST /api/v1/auth/onboarding/{email,sms}/{send,verify}
- POST /api/v1/auth/{email,sms}/{send,verify}
- POST /api/v1/auth/signup, login, refresh, logout

작성한 테스트
- `backend/src/test/java/com/nidus/twinly/auth/controller/AuthControllerUnitTest.java` (@WebMvcTest 슬라이스, 20건)
- `backend/src/test/java/com/nidus/twinly/auth/service/AuthServiceUnitTest.java` (순수 단위, 25건)
- `backend/src/test/java/com/nidus/twinly/auth/service/CodeVerificationServiceUnitTest.java` (순수 단위, 4건)
- `backend/src/test/java/com/nidus/twinly/auth/integration/AuthIntegrationTest.java` (end-to-end, **18건**)

아래는 **미해결** 항목이다. 해결된 항목은 이 문서에서 제거하고 회귀 테스트로 대체한다.
이력은 [_summary.md](_summary.md) 참조.

---

## POST /api/v1/auth/signup

### 1. 온보딩 프로필이 비어 있으면 500으로 떨어진다
- **심각도**: medium
- **증상**: 이름/소속/생년월일 등을 아직 입력하지 않은 익명 세션이 SMS·EMAIL 인증만 마치고 signup을 호출하면 `INTERNAL_ERROR`(500)가 반환된다. 클라이언트 입력 부족이므로 4xx여야 한다.
- **재현 조건**: `anon_sessions.family_name`(또는 given_name/affiliation/affiliation_number/birth_date)이 NULL인 상태에서 signup 호출. 해당 컬럼들은 스키마상 nullable이다(`V1__init_schema.sql:110~117`).
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:238-242` — null 가능 값을 그대로 `blindIndexHasher.hash(...)`에 넘김
  - `backend/src/main/java/com/nidus/twinly/common/crypto/BlindIndexHasher.java:25` — `plainText.getBytes(...)`에서 NPE
  - `backend/src/main/java/com/nidus/twinly/common/crypto/BlindIndexHasher.java:28` — `catch (Exception e)`가 NPE까지 삼켜 `IllegalStateException`으로 바꿈 → `GlobalExceptionHandler`의 `handleUnexpected`로 500
- **제안**: signup 진입부에서 필수 온보딩 필드 null 검사를 하고 `BusinessException(신규 ONBOARDING_NOT_COMPLETED)`(4xx)로 변환한다. 부가로 `BlindIndexHasher`의 `catch (Exception)`은 `GeneralSecurityException` 등으로 좁혀 프로그래밍 오류(NPE)를 감추지 않게 한다.

---

## POST /api/v1/auth/login

### 2. 사용한 smsVerifiedToken이 무효화되지 않아 재사용(리플레이)이 가능하다
- **심각도**: medium
- **증상**: 로그인에 한 번 쓴 `smsVerifiedToken`으로 만료 전(30분)까지 몇 번이든 다시 로그인해 새 액세스/리프레시 토큰 쌍을 계속 발급받을 수 있다. 로그아웃해도 같은 토큰으로 곧바로 재로그인된다.
- **재현 조건**: `/api/v1/auth/sms/verify` 응답의 `smsVerifiedToken`으로 `/api/v1/auth/login`을 2회 이상 호출 → 매번 200과 새 토큰.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:311-320` — `verifySession`은 만료만 확인하고 세션을 소모하지 않음
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:329-338` — `login`이 사용한 `VerificationSession`을 삭제/무효화하지 않음
- **제안**: 로그인 성공 시 해당 `VerificationSession`을 삭제하거나 `verifiedToken`을 null로 만들어 1회용으로 만든다(one-time use). 더불어 `verification_sessions` 행은 어디서도 정리되지 않으므로 만료분 배치 삭제를 함께 고려한다.
- **주의**: `AuthIntegrationTest.login_twice_in_same_second_issues_distinct_tokens`(JWT `jti` 회귀 테스트)가 같은 토큰으로 로그인을 두 번 호출한다. 이 건을 고치면 그 테스트가 깨지므로, 인증 세션을 두 개 만들어 로그인하는 방식으로 함께 고쳐야 한다.

---

## POST /api/v1/auth/onboarding/{email,sms}/verify, POST /api/v1/auth/{email,sms}/verify

### 3. 이미 인증 완료된 세션을 같은 코드로 반복 verify할 수 있다
- **심각도**: low
- **증상**: 온보딩 verify는 `verifiedAt`만 덮어쓰고, 로그인용 verify는 호출할 때마다 **새 `verifiedToken`을 재발급**한다. 코드가 1회용이 아니다.
- **재현 조건**: 같은 `verificationToken` + 정답 코드로 verify를 2회 호출 → 둘 다 성공, 로그인용은 서로 다른 `verifiedToken` 2개가 유효하게 존재.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:209-213`
  - `backend/src/main/java/com/nidus/twinly/auth/service/CodeVerificationService.java:36-38`
- **제안**: 이미 `verifiedAt != null`이면 코드 재검증을 막고 기존 결과를 반환하거나 `VERIFICATION_NOT_FOUND`로 처리한다.

---

## POST /api/v1/auth/email/send, POST /api/v1/auth/sms/send

## POST /api/v1/auth/refresh

### 4. 저장된 RefreshToken의 expiresAt을 검증하지 않는다
- **심각도**: low
- **증상**: `refresh_tokens.expires_at`은 저장만 하고 확인하지 않는다. 현재는 `jwtService.parseRefreshTokenUserId`가 JWT `exp`를 검증해 실질적 노출은 없지만, JWT 만료와 DB 만료가 어긋나면 만료 토큰이 통과할 수 있다.
- **재현 조건**: DB의 `expires_at`만 과거로 바꾸고 JWT는 유효한 상태에서 refresh 호출 → 200.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:349-358`
- **제안**: 조회한 `RefreshToken.getExpiresAt()`이 과거면 `INVALID_REFRESH_TOKEN`으로 처리한다(방어적 이중 검증).

---

## 공통 / 정리
