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

### 3. 인증번호 시도 횟수 제한이 없다 (brute force)
- **심각도**: medium
- **증상**: 6자리(=100만 경우의 수) 코드에 대해 유효 시간 5분 동안 **무제한** 시도가 가능하다. 실패 카운트도 잠금도 없다.
- **재현 조건**: 발급된 `verificationToken`으로 코드만 바꿔가며 verify를 반복 호출 → 계속 422만 반환하고 차단되지 않음.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:198-214` — `verifyAnonSession`, 실패 횟수 누적 없음
  - `backend/src/main/java/com/nidus/twinly/auth/service/CodeVerificationService.java:25-40` — 동일
- **제안**: 세션에 `attemptCount` 컬럼을 추가해 N회(예: 5회) 실패 시 세션을 무효화하고 재발송을 요구한다.

### 4. 이미 인증 완료된 세션을 같은 코드로 반복 verify할 수 있다
- **심각도**: low
- **증상**: 온보딩 verify는 `verifiedAt`만 덮어쓰고, 로그인용 verify는 호출할 때마다 **새 `verifiedToken`을 재발급**한다. 코드가 1회용이 아니다.
- **재현 조건**: 같은 `verificationToken` + 정답 코드로 verify를 2회 호출 → 둘 다 성공, 로그인용은 서로 다른 `verifiedToken` 2개가 유효하게 존재.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:209-213`
  - `backend/src/main/java/com/nidus/twinly/auth/service/CodeVerificationService.java:36-38`
- **제안**: 이미 `verifiedAt != null`이면 코드 재검증을 막고 기존 결과를 반환하거나 `VERIFICATION_NOT_FOUND`로 처리한다.

---

## POST /api/v1/auth/email/send, POST /api/v1/auth/sms/send

### 5. 가입 여부가 응답으로 그대로 노출된다 (계정 열거)
- **심각도**: low
- **증상**: 미가입이면 404 `EMAIL_NOT_REGISTERED` / `PHONE_NOT_REGISTERED`, 가입이면 200이 나와 임의의 이메일·전화번호의 가입 여부를 확인할 수 있다.
- **재현 조건**: 임의 이메일로 `/api/v1/auth/email/send` 호출 → status로 가입 여부 판별.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:124-126`
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:152-154`
- **제안**: 제품 정책상 의도된 UX일 수 있다. 유지한다면 최소한 IP/연락처 단위 rate limit을 걸어 대량 조회를 막는다.

### 6. 발송 요청에 rate limit이 없고 세션 행이 계속 쌓인다
- **심각도**: low
- **증상**: 로그인용 send는 호출할 때마다 `VerificationSession`을 새로 `save`한다. 반복 호출 시 SMS/메일 비용이 그대로 발생하고 `verification_sessions` 행이 무한 증가한다(삭제 로직 없음).
- **재현 조건**: `/api/v1/auth/sms/send`를 연속 호출 → 매번 새 행 생성 + 매번 SOLAPI 발송.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:131-132` (email), `:159-160` (sms)
- **제안**: 연락처별 재발송 쿨다운(예: 60초)과 일일 발송 한도를 두고, 만료 세션 정리 배치를 추가한다. (온보딩 send는 `upsertVerificationSession`으로 행이 늘지 않으므로 이 문제는 없다.)

---

## POST /api/v1/auth/refresh

### 7. 저장된 RefreshToken의 expiresAt을 검증하지 않는다
- **심각도**: low
- **증상**: `refresh_tokens.expires_at`은 저장만 하고 확인하지 않는다. 현재는 `jwtService.parseRefreshTokenUserId`가 JWT `exp`를 검증해 실질적 노출은 없지만, JWT 만료와 DB 만료가 어긋나면 만료 토큰이 통과할 수 있다.
- **재현 조건**: DB의 `expires_at`만 과거로 바꾸고 JWT는 유효한 상태에서 refresh 호출 → 200.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/auth/service/AuthService.java:349-358`
- **제안**: 조회한 `RefreshToken.getExpiresAt()`이 과거면 `INVALID_REFRESH_TOKEN`으로 처리한다(방어적 이중 검증).

---

## 공통 / 정리

### 8. 사용되지 않는 result DTO (죽은 코드)
- **심각도**: low
- **증상**: `AuthLoginResult`, `AuthSignupResult`, `AuthRefreshResult`는 어디서도 참조되지 않는다. 실제로는 세 API 모두 `AuthTokenResult`를 반환한다.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/auth/dto/result/AuthLoginResult.java`
  - `backend/src/main/java/com/nidus/twinly/auth/dto/result/AuthSignupResult.java`
  - `backend/src/main/java/com/nidus/twinly/auth/dto/result/AuthRefreshResult.java`
- **제안**: 삭제한다. (응답 DTO `AuthLoginResponse`/`AuthSignupResponse`/`AuthRefreshResponse`도 구조가 동일하므로 하나로 합칠지 여부는 API 문서 가독성과의 trade-off로 판단.)
