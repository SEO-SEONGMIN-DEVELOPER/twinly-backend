# REST API 테스트 라운드 요약 (springdoc 스펙 기준)

## 스코프 확정 방법

`GET /v3/api-docs`(springdoc)를 통합 테스트 하니스에서 실제로 덤프해 오퍼레이션을 열거했다.
전체 77개 중 springwolf 문서 엔드포인트 3개(`/springwolf/**`)를 제외한 **REST 74개**가 대상이다.

## 커버리지

| 구분 | 커버리지 |
|---|---|
| 단위/슬라이스 (`@WebMvcTest`) | **74 / 74** |
| 통합 (`@SpringBootTest` + 실제 MySQL) | **74 / 74** |

## 최종 실행 결과

| 태스크 | 테스트 수 | 실패 |
|---|---|---|
| `./gradlew test` (단위/슬라이스) | 351 | **0** |
| `./gradlew integrationTest` (통합) | 110 | **3** |

통합 3건의 실패는 모두 **운영 코드 버그**가 원인이다. 스킬 원칙에 따라 초록불을 위해 테스트를 비틀지 않았고,
운영 코드도 수정하지 않았다. 해당 테스트에는 `[의도된 실패 - BUG-xxx]` 주석을 달아 두었다.

## 이번 라운드에서 새로 확인한 운영 코드 버그

| ID | 도메인 | 영향 | 심각도 | 상세 |
|---|---|---|---|---|
| BUG-ONB-01 | onboarding | `POST /api/v1/onboarding/survey-answers` **항상 500** | **critical** | [onboarding.md](onboarding.md) |
| BUG-PHOTO-01 | onboarding, me | 프로필 사진 최초 commit **항상 500** (`anon_session_photos`, `photos`) | **critical** | [onboarding.md](onboarding.md), [me.md](me.md) |
| BUG-AUTH-01 | auth | 같은 초의 재로그인/즉시 재발급 **500**, 토큰 회전 무효화 | **high** | [auth.md](auth.md) |

세 건 모두 단위/슬라이스 테스트로는 드러나지 않는다. Repository 쿼리 파생, 실제 SQL 생성, 실제 유니크 제약이
개입해야 나타나는 결함이라 **통합 테스트에서만 잡힌다**. 이번 라운드의 통합 커버리지 확장(39→74)이 직접적인 계기였다.

## 테스트 코드 쪽 수정 (운영 코드 아님)

라운드 시작 시 이미 빨간불이던 2건은 테스트의 기대값 오류였으므로 테스트를 고쳤다.

- `SeasonIntegrationTest`: `participatedInAt` 기대값을 `...Z` → `2026-07-01T09:00:00+09:00`.
  운영 코드의 `KstInstantSerializer`가 KST 오프셋으로 직렬화하는 것이 **의도된 설계**다.
- `OnboardingIntegrationTest`: presign 실패 기대 상태코드를 422 → **415**.
  `ErrorCode.UNSUPPORTED_IMAGE_TYPE`이 `UNSUPPORTED_MEDIA_TYPE`으로 매핑되어 있다.

## 도메인별 문서

[activity](activity.md) · [anon](anon.md) · [auth](auth.md) · [block](block.md) · [chat](chat.md) ·
[connection](connection.md) · [legal](legal.md) · [main](main.md) · [me](me.md) · [onboarding](onboarding.md) ·
[people](people.md) · [push](push.md) · [report](report.md) · [season](season.md)
