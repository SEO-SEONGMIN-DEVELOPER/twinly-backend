# onboarding 도메인 테스트 발견사항

`OnboardingController` 담당 엔드포인트 11개에 대한 단위/슬라이스/통합 테스트 작성 중 발견한 **미해결** 항목이다.
해결된 항목은 이 문서에서 제거하고 회귀 테스트로 대체한다. 이력은 [_summary.md](_summary.md) 참조.

---

## POST /api/v1/onboarding/survey-answers

### 1. `saveAllSurveyAnswer`의 `@Transactional`은 자기 호출이라 동작하지 않는다

- **증상**: `surveyAnswer()`가 같은 클래스의 `saveAllSurveyAnswer()`를 직접 호출하므로 프록시를 타지 않아
  `@Transactional`이 적용되지 않는다. 지금은 호출자(`surveyAnswer`)가 이미 트랜잭션이라 문제가 없지만,
  "이 메서드는 자체 트랜잭션을 갖는다"는 오해를 만든다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:105-106`
- **심각도**: low
- **제안**: 내부 전용이면 `private`로 내리고 어노테이션을 제거한다.

---

## PUT /api/v1/onboarding/basic-info

### 2. `INVALID_ANON_SESSION` 분기는 사실상 도달 불가하다 (참고)

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
