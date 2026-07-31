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

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
