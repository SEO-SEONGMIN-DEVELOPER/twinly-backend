# 테스트 발견사항 — 남은 할 일

REST 74개 오퍼레이션(springdoc `/v3/api-docs` 기준, springwolf 문서 3개 제외)과 WebSocket 채널에 대해
단위/슬라이스·통합 테스트를 작성하며 확인한 항목 중 **아직 처리하지 않은 것**의 인덱스다.

해결된 항목은 각 문서에서 제거한다. 해결 이력은 회귀 테스트와 커밋 메시지가 갖는다.

## 현재 상태

| 태스크 | 테스트 수 | 실패 |
|---|---|---|
| `./gradlew test` (단위/슬라이스) | 378 | **0** |
| `./gradlew integrationTest` (통합, Testcontainers) | 112 | **0** |

통합 커버리지는 REST 74/74다.

## 남은 항목 (125건)

| 문서 | 항목 | high | medium | low/info |
|---|---:|---:|---:|---:|
| [people](people.md) | 20 | 2 | 8 | 8 |
| [onboarding](onboarding.md) | 14 | 0 | 7 | 7 |
| [chat](chat.md) | 12 | 2 | 4 | 6 |
| [chat-websocket](chat-websocket.md) | 10 | 2 | 2 | 3 |
| [auth](auth.md) | 9 | 1 | 3 | 5 |
| [me](me.md) | 9 | 1 | 3 | 5 |
| [push](push.md) | 7 | 0 | 2 | 5 |
| [season](season.md) | 7 | 0 | 1 | 5 |
| [connection](connection.md) | 6 | 0 | 2 | 4 |
| [connection-websocket](connection-websocket.md) | 6 | 0 | 2 | 2 |
| [legal](legal.md) | 6 | 0 | 2 | 4 |
| [main](main.md) | 6 | 0 | 2 | 4 |
| [anon](anon.md) | 4 | 0 | 1 | 3 |
| [activity](activity.md) | 3 | 0 | 0 | 3 |
| [block](block.md) | 3 | 0 | 0 | 3 |
| [report](report.md) | 3 | 0 | 0 | 3 |
| **합계** | **125** | **8** | **39** | **78** |

## 우선 처리 대상 — high 8건

채팅 도메인에 절반이 몰려 있다. 기능 자체가 동작하지 않는 건이 포함되어 있어 여기부터 보는 것이 맞다.

| 도메인 | 내용 | 문서 |
|---|---|---|
| chat-websocket | `chat_room_participations` 행을 만드는 코드가 없어 `/app/chat/read`가 운영에서 항상 실패 | [chat-websocket.md](chat-websocket.md) |
| chat-websocket | `ChatChangedEvent` 발행처가 없어 `/user/queue/chat/index` 채널이 영구 무음 | [chat-websocket.md](chat-websocket.md) |
| chat | 메시지가 0건인 방이 목록에 있으면 응답 변환 중 NPE로 500 | [chat.md](chat.md) |
| chat | 매칭 0건 유저의 목록 조회 시 빈 `IN` 절로 SQL 오류 가능 (미검증) | [chat.md](chat.md) |
| me | `profilePhotoCommit`에 `@Transactional`이 없어 사진 교체가 DB에 반영되지 않음 | [me.md](me.md) |
| auth | 익명 세션(부모)을 자식 행보다 먼저 삭제 → 커밋 flush 시 FK 위반 가능 | [auth.md](auth.md) |
| people | `maxPoints`에 검증이 없어 음수를 주면 500 | [people.md](people.md) |
| people | 필수 쿼리 파라미터 누락 시 400이 아니라 500 (전역 이슈) | [people.md](people.md) |

## 처리 완료 (2026-07-29)

각 항목은 회귀 테스트로 고정했다. 상세 분석은 해당 커밋에 있다.

| 내용 | 회귀 테스트 |
|---|---|
| `key`가 MySQL 예약어인데 엔티티에 인용이 없어 사진 INSERT가 항상 500 (`AnonSessionPhoto`, `Photo`) | `OnboardingIntegrationTest.profilePhotoCommit_end_to_end`, `MeIntegrationTest` 동명 케이스 |
| `SurveyAnswer.qId` 필드명이 JavaBeans 규약상 프로퍼티 `QId`로 해석돼 설문 답변 저장이 항상 500 (컬럼도 `question_id`로 개명) | `OnboardingIntegrationTest.surveyAnswer_end_to_end` |
| `SurveyLoader.lastKey()`가 마지막 문항 id가 아닌 문항 **개수**를 반환해, 노출 순서 17번째에서 페르소나가 부분 생성 | `SurveyLoaderUnitTest` (2건) |
| JWT에 `jti`/`iat`가 없어 같은 초 발급 토큰이 동일 → 재로그인 500, 재발급 500, 토큰 회전 무효 | `AuthIntegrationTest.login_twice_in_same_second_issues_distinct_tokens` |

네 건 모두 단위/슬라이스 테스트로는 원리적으로 잡히지 않는다. 목이 실제 SQL 생성, 쿼리 파생, 유니크 제약,
파일 로딩을 전부 가리기 때문이다. **실제 DB에 쓰기까지 하는 통합 테스트에서만** 드러났다.

## 도메인별 문서

[activity](activity.md) · [anon](anon.md) · [auth](auth.md) · [block](block.md) · [chat](chat.md) ·
[chat-websocket](chat-websocket.md) · [connection](connection.md) · [connection-websocket](connection-websocket.md) ·
[legal](legal.md) · [main](main.md) · [me](me.md) · [onboarding](onboarding.md) ·
[people](people.md) · [push](push.md) · [report](report.md) · [season](season.md)
