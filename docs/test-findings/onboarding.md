# onboarding 도메인 테스트 발견사항

`OnboardingController` 담당 엔드포인트 11개에 대한 단위/슬라이스/통합 테스트 작성 중 발견한 **미해결** 항목이다.
해결된 항목은 이 문서에서 제거하고 회귀 테스트로 대체한다. 이력은 [_summary.md](_summary.md) 참조.

---

## POST /api/v1/onboarding/survey-answers

### 1. 마지막 문항을 다시 답하면 페르소나 요소가 중복 저장된다

- **증상**
  `saveAllSurveyAnswer`는 삭제/중복 체크 없이 `save`만 하므로, 트리거 문항을 재답변할 때마다
  같은 내용의 `anon_session_persona_elements` 행이 계속 늘어난다. 이후 AI 프롬프트에 같은 특성이 N번 들어간다.
- **재현 조건**: 트리거가 되는 문항(설문 파일 순서상 마지막 문항. 현재 `qId=4`)에 A로 답한 뒤 B로 다시 답변 → 페르소나 요소가 2배로 쌓인다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:106-114`
- **심각도**: medium
- **제안** 변환 전에 해당 세션의 설문 유래 페르소나 요소를 지우거나(dimension 기준 delete 후 insert),
  이미 변환이 끝난 세션이면 재실행하지 않도록 가드를 둔다.

### 2. `saveAllSurveyAnswer`의 `@Transactional`은 자기 호출이라 동작하지 않는다

- **증상**: `surveyAnswer()`가 같은 클래스의 `saveAllSurveyAnswer()`를 직접 호출하므로 프록시를 타지 않아
  `@Transactional`이 적용되지 않는다. 지금은 호출자(`surveyAnswer`)가 이미 트랜잭션이라 문제가 없지만,
  "이 메서드는 자체 트랜잭션을 갖는다"는 오해를 만든다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:105-106`
- **심각도**: low
- **제안**: 내부 전용이면 `private`로 내리고 어노테이션을 제거한다.

---

## POST /api/v1/onboarding/interests

### 3. 관심사 목록의 원소가 null이면 500이 난다

- **증상**: `OnboardingInterestsRequest.interests`에는 `@NotNull`만 있고 원소 제약이 없다.
  `{"interests": [null]}` 요청이 검증을 통과해 `explanation TEXT NOT NULL` 컬럼에 null을 insert하려다
  `DataIntegrityViolationException` → `GlobalExceptionHandler`의 catch-all에 걸려 **500**이 된다. (400이어야 할 입력 오류)
- **재현 조건**: `POST /api/v1/onboarding/interests` 바디 `{"interests": [null]}` 또는 `[""]`(빈 문자열은 저장은 되나 무의미)
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/onboarding/dto/request/OnboardingInterestsRequest.java:7`
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:119-121`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:63` (`explanation TEXT NOT NULL`)
- **심각도**: medium
- **제안**: `@NotNull List<@NotBlank String> interests` 로 원소 제약을 걸고, 필요하면 `@Size`로 개수 상한도 둔다.

### 4. 관심사를 다시 제출하면 이전 관심사가 남아 중복 누적된다

- **증상**: 기존 INTERESTS 페르소나 요소를 지우지 않고 append만 하므로, 사용자가 관심사 화면에서 뒤로 갔다가
  다시 제출하면 관심사가 두 배로 쌓인다. (온보딩 화면 재진입은 흔한 시나리오)
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:117-122`
- **심각도**: medium
- **제안**: 세션의 `INTERESTS` 차원 요소를 삭제한 뒤 저장하는 "치환" 의미로 구현한다.

---

## POST /api/v1/onboarding/profile/nickname/check, PUT /api/v1/onboarding/profile/nickname

### 5. 닉네임 정책이 금지어 검사뿐이다

- **증상**: `validateAndNormalizeNickname`은 trim + 금지어 4개 확인만 한다. 길이 상한, 허용 문자,
  공백/특수문자 규칙이 없어 500자 닉네임이나 제어문자도 통과한다. (`INVALID_NICKNAME` 코드가 사실상 금지어 전용)
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:224-236`
- **심각도**: low
- **제안**: 길이(예: 2~20자)와 허용 문자 정규식을 `@Pattern`/`@Size`로 request DTO에 선언해 400으로 거르고,
  금지어처럼 도메인 지식이 필요한 것만 서비스에 남긴다.

---

## POST /api/v1/onboarding/consents

### 6. 최신/시행 중인 정책 버전인지 확인하지 않는다

- **증상**: `PolicyCatalog.loadByKey`는 `effective_at` 필터 없이 모든 버전을 키로 만든다.
  따라서 클라이언트가 **과거 버전**(`version: 1`, 이미 v2가 시행 중)으로 동의를 보내도 그대로 저장된다.
  조회 API(`LegalService.policies`)는 최신 버전만 내려주므로 정상 클라이언트는 문제없지만, 서버 검증은 없다.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/legal/service/PolicyCatalog.java:22-33`
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:186-198`
- **심각도**: low
- **제안**: 동의 대상은 "현재 시행 중인 최신 버전"만 허용하고, 그 외 버전은 `POLICY_NOT_FOUND`로 거절한다.

---

## DELETE /api/v1/onboarding/consents

### 7. 존재하지 않는 정책/버전을 철회 요청해도 200을 반환한다 (등록 API와 비대칭)

- **증상**: `grantConsents`는 정책을 못 찾으면 `POLICY_NOT_FOUND(404)`를 던지는데,
  `revokeConsents`는 `filter(policy -> policy != null)`로 **조용히 무시**하고 200을 반환한다.
  클라이언트는 철회에 실패했는데 성공으로 인지한다. 요청 전부가 잘못된 경우에도 200이다.
- **재현 조건**: `DELETE /api/v1/onboarding/consents` 바디 `{"grants":[{"policyId":"없는정책","version":"1"}]}` → 200
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:209-221`
- **심각도**: medium
- **제안**: 등록과 동일하게 조회 실패 시 `POLICY_NOT_FOUND`를 던져 동작을 대칭으로 맞춘다.

### 8. DELETE에 요청 바디를 요구한다

- **증상**: `@DeleteMapping` + `@RequestBody`. 일부 HTTP 클라이언트/프록시/캐시는 DELETE 바디를 버리거나
  전달하지 않아(RFC 9110에서도 의미가 정의돼 있지 않음) 400으로 실패할 수 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/controller/OnboardingController.java:110-113`
- **심각도**: low
- **제안**: `POST /api/v1/onboarding/consents/revoke` 처럼 바디를 자연스럽게 쓰는 형태로 바꾸거나,
  철회 대상을 쿼리 파라미터로 받는다.

---

## PUT /api/v1/onboarding/basic-info

### 9. 생년월일에 미래 날짜가 들어올 수 있다

- **증상**: `birthDate`에 `@NotNull`만 있어 `"2999-01-01"`도 통과해 그대로 저장된다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/dto/request/OnboardingBasicInfoRequest.java:15`
- **심각도**: low
- **제안**: `@Past`(또는 `@PastOrPresent`) 추가. 필요하면 연령 하한도 함께 검증한다.

### 10. `INVALID_ANON_SESSION` 분기는 사실상 도달 불가하다 (참고)

- **증상**: 인증 리졸버(`CurrentAnonSessionArgumentResolver` → `AnonService.resolveByToken`)가 이미 세션을 조회·검증했으므로,
  서비스의 `findById(...).orElseThrow(INVALID_ANON_SESSION)`는 같은 트랜잭션 안에서 실질적으로 실패하지 않는다.
  버그는 아니지만 "스냅샷을 받고 다시 조회한다"는 이중 조회 구조라 의도를 문서화하거나 정리할 여지가 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:66-68`
- **심각도**: low
- **제안**: 현 구조 유지가 합리적이면 그대로 두되(스냅샷은 값 객체, 수정은 엔티티로), 중복 조회 비용은 인지하고 있을 것.

---

## GET /api/v1/onboarding/survey-questions, POST /api/v1/onboarding/profile/photo/presign, POST /api/v1/onboarding/profile/photo/commit

발견된 이슈 없음.
(단, presign/commit은 `PresignService`/`PhotoCommitService`가 소유자 검증·contentType 화이트리스트·업로드 완료 확인을
이미 수행하고 있어 온보딩 서비스 계층에는 추가 결함이 보이지 않았다.)
