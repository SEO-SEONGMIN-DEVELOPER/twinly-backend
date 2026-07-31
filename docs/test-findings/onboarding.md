# onboarding 도메인 테스트 발견사항

`OnboardingController` 담당 엔드포인트 11개에 대한 단위/슬라이스/통합 테스트 작성 중 발견한 **미해결** 항목이다.
해결된 항목은 이 문서에서 제거하고 회귀 테스트로 대체한다. 이력은 [_summary.md](_summary.md) 참조.

---

## GET /api/v1/onboarding/survey-questions, POST /api/v1/onboarding/profile/photo/presign, POST /api/v1/onboarding/profile/photo/commit

발견된 이슈 없음.
(단, presign/commit은 `PresignService`/`PhotoCommitService`가 소유자 검증·contentType 화이트리스트·업로드 완료 확인을
이미 수행하고 있어 온보딩 서비스 계층에는 추가 결함이 보이지 않았다.)

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
