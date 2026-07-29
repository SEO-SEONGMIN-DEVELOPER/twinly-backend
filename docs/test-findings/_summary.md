# 테스트 발견사항 — 남은 할 일

REST 74개 오퍼레이션(springdoc `/v3/api-docs` 기준, springwolf 문서 3개 제외)과 WebSocket 채널에 대해
단위/슬라이스·통합 테스트를 작성하며 확인한 항목 중 **아직 처리하지 않은 것**의 인덱스다.

해결된 항목은 각 문서에서 제거한다. 해결 이력은 회귀 테스트와 커밋 메시지가 갖는다.

## 현재 상태

| 태스크 | 테스트 수 | 실패 |
|---|---|---|
| `./gradlew test` (단위/슬라이스) | 384 | **0** |
| `./gradlew integrationTest` (통합, Testcontainers) | 118 | **0** |

통합 커버리지는 REST 74/74다.

> 통합 테스트는 로컬 `.env`의 암호화 키를 필요로 한다. 키가 없으면 `AesGcmEncryptor` 생성에 실패해
> 컨텍스트 기동 단계에서 전부 죽는다. CI에 붙일 때 시크릿 주입이 선행되어야 한다.

## 남은 항목 (108건)

| 문서 | 항목 | high | medium | low/info |
|---|---:|---:|---:|---:|
| [people](people.md) | 18 | 0 | 8 | 8 |
| [chat](chat.md) | 10 | 0 | 4 | 6 |
| [onboarding](onboarding.md) | 10 | 0 | 4 | 6 |
| [auth](auth.md) | 8 | 0 | 3 | 5 |
| [chat-websocket](chat-websocket.md) | 7 | 2 | 2 | 3 |
| [me](me.md) | 7 | 0 | 2 | 5 |
| [push](push.md) | 7 | 0 | 2 | 5 |
| [connection](connection.md) | 6 | 0 | 2 | 4 |
| [legal](legal.md) | 6 | 0 | 2 | 4 |
| [main](main.md) | 6 | 0 | 2 | 4 |
| [season](season.md) | 6 | 1 | 1 | 4 |
| [anon](anon.md) | 4 | 0 | 1 | 3 |
| [connection-websocket](connection-websocket.md) | 4 | 0 | 2 | 2 |
| [activity](activity.md) | 3 | 0 | 0 | 3 |
| [block](block.md) | 3 | 0 | 0 | 3 |
| [report](report.md) | 3 | 0 | 0 | 3 |
| **합계** | **108** | **3** | **35** | **70** |

## 우선 처리 대상 — high 3건

| 도메인 | 내용 | 성격 |
|---|---|---|
| chat-websocket | `chat_room_participations` 행을 만드는 코드가 없어 `/app/chat/read`가 운영에서 항상 실패 | **기능 미구현.** 매칭 성사 시 방·참여를 만드는 흐름 자체가 없음 |
| chat-websocket | `ChatChangedEvent` 발행처가 없어 `/user/queue/chat/index` 채널이 영구 무음 | **기능 미구현.** 목록 갱신 시점이 제품 결정 사항 |
| season | 활성 시즌 행이 없으면 `CurrentSeasonReader`를 쓰는 경로가 전부 500 | 폭발 반경이 시즌에 그치지 않음. `GET /chat/rooms`도 함께 죽는 것을 실행으로 확인 |

앞 두 건은 버그 수정이 아니라 **없는 기능을 만드는 일**이다. "매칭 성사 시점에 방을 만들지, 첫 메시지에 만들지"가
정해져야 착수할 수 있다. 세 번째는 기동 시 fail-fast 또는 시즌 시드 추가로 해결된다.

## 남은 것들의 반복 패턴

개별 항목으로 흩어져 있지만 원인은 몇 가지로 묶인다. 하나씩 고치는 것보다 패턴 단위로 처리하는 편이 효율적이다.

| 패턴 | 대략 건수 | 내용 |
|---|---:|---|
| **입력 검증 누락** | 8 | `@NotNull`만 있고 원소·범위·형식 제약이 없어 400이어야 할 것이 500 (`limit` 상한, 생년월일 미래, 공백 문자열, 관심사 null 원소) |
| **중복 누적** | 3 | 재제출 시 기존 행을 지우지 않고 append (신고, 관심사, 설문 유래 페르소나) |
| **동시 요청 경합** | 5 | check-then-act 사이에 다른 요청이 끼어들어 유니크 제약 위반이 500으로 샘 |
| **조회/변경 API 비대칭** | 4 | 등록은 404를 던지는데 철회는 조용히 무시하고 200, DELETE에 바디 요구 |
| **죽은 코드** | 3 | 호출자 없는 DTO·쿼리 (`AuthLoginResult` 등, ai-chat 이력 조회 쿼리) |

**동시 요청 경합**은 우선순위를 낮게 두었다. 같은 사용자의 재요청은 순차 멱등 처리로 이미 막히고, 남은 것들은
서로 다른 사용자가 겹칠 여지가 없는 자기 데이터 조작이다(차단·시즌 참가·즐겨찾기·푸시 토큰). 실제 발생 확률이
있는 닉네임 선점만 처리했다. 트래픽이 붙은 뒤 5xx 지표에 `DataIntegrityViolationException`이 찍히면 그때 대응한다.

## 처리 완료

각 항목은 회귀 테스트로 고정했다. 상세 분석은 해당 커밋에 있다.

### 1차 (2026-07-29)

| 내용 | 회귀 테스트 |
|---|---|
| `key`가 MySQL 예약어인데 엔티티에 인용이 없어 사진 INSERT가 항상 500 | `OnboardingIntegrationTest.profilePhotoCommit_end_to_end`, `MeIntegrationTest` 동명 케이스 |
| `SurveyAnswer.qId`가 JavaBeans 규약상 프로퍼티 `QId`로 해석돼 설문 답변 저장이 항상 500 (컬럼도 `question_id`로 개명) | `OnboardingIntegrationTest.surveyAnswer_end_to_end` |
| `SurveyLoader.lastKey()`가 마지막 문항 id가 아닌 문항 **개수**를 반환해 노출 순서 17번째에서 페르소나가 부분 생성 | `SurveyLoaderUnitTest` |
| JWT에 `jti`/`iat`가 없어 같은 초 발급 토큰이 동일 → 재로그인 500, 재발급 500, 토큰 회전 무효 | `AuthIntegrationTest.login_twice_in_same_second_issues_distinct_tokens` |

### 2차 (2026-07-29)

| 내용 | 회귀 테스트 |
|---|---|
| 메시지가 0건인 방이 목록에 있으면 응답 변환 중 NPE로 목록 전체가 500 | `ChatIntegrationTest.rooms_when_room_has_no_message_returns_room_without_last_message` |
| 필수 쿼리 파라미터 누락·파라미터 검증 실패가 400이 아니라 500 (전역) | `PeopleControllerUnitTest`, `MeControllerUnitTest` 각 `..._missing_required_param_returns_400` |
| `maxPoints`가 1 미만이면 500 또는 조용한 빈 시계열 | `PeopleControllerUnitTest.intimacySeries_with_non_positive_maxPoints_returns_400` |
| `profilePhotoCommit`에 `@Transactional`이 없어 사진 교체가 DB에 미반영 | `MeIntegrationTest.profilePhotoCommit_replaces_existing_photo` |
| 회원가입 시 부모(익명 세션)를 자식보다 먼저 삭제해 커밋 flush 시 FK 위반 | `AuthIntegrationTest.signup_cleans_up_anon_session_and_children` |

### 3차 — 순차 재요청 멱등 (2026-07-29)

변경 API 37개를 전수 감사했다. 대다수는 이미 `existsBy → return` / `ifPresentOrElse` / 값 덮어쓰기 패턴으로
재요청에 안전했고, 아래 4곳만 비어 있었다.

| 내용 | 회귀 테스트 |
|---|---|
| ai-chat 시작을 두 번 호출하면 500 (모델 비용도 이중 발생) | `OnboardingIntegrationTest.aiChatStart_is_idempotent` |
| 같은 turnIndex로 두 번 답하면 500 (DETAIL 요소 중복 누적) | `OnboardingIntegrationTest.aiChatMessage_is_idempotent` |
| 탈퇴 신청 재요청이 409라 응답 유실 시 `recoverableUntil`을 못 받음 | `MeServiceUnitTest.withdraw_already_requested_is_idempotent` |
| 망설임 답변 재전송이 409 (같은 내용이어도 거절) | `MeServiceUnitTest.hesitationsAnswer_same_answer_is_idempotent` |
| 닉네임 선점 경합이 500 | `OnboardingServiceUnitTest.profileNickname_when_lost_race_throws_already_used` |

### 결함이 아니었던 것

| 기록된 내용 | 확인 결과 |
|---|---|
| 매칭 0건 유저의 목록 조회 시 네이티브 쿼리의 빈 `IN` 절이 SQL 오류를 낸다 (미검증) | **오진.** 현재 스택(Hibernate 7 + MySQL)에서 정상 동작. 운영 코드는 그대로 두고 경계 테스트만 남김 (`ChatIntegrationTest.rooms_when_no_match_returns_empty_list`) |

## 이번 라운드들에서 확인한 것

- **단위 테스트로는 원리적으로 잡히지 않는 결함이 있다.** 목이 실제 SQL 생성, 쿼리 파생, 유니크 제약,
  파일 로딩을 전부 가린다. 1차 4건은 전부 통합 테스트에서만 드러났다.
- **통합 테스트도 사각지대를 갖는다.** 베이스 클래스가 `@Transactional`이라 서비스가 테스트의 트랜잭션에
  편승한다. 트랜잭션 경계 자체가 결함인 버그(`@Transactional` 누락)는 `propagation = NOT_SUPPORTED`로
  주변 트랜잭션을 끄지 않으면 재현되지 않는다.
- **"미검증"으로 기록된 항목은 그대로 믿으면 안 된다.** high로 분류됐던 빈 `IN` 절 건은 실행해 보니 결함이 아니었다.
- **버그를 고친 뒤에는 그 버그를 우회하려고 넣었던 장치도 함께 걷어내야 한다.** `refresh_end_to_end`의
  `Thread.sleep(1_100)`을 제거하자 "발급 직후 재발급" 경로가 비로소 검증되기 시작했다.

## 도메인별 문서

[activity](activity.md) · [anon](anon.md) · [auth](auth.md) · [block](block.md) · [chat](chat.md) ·
[chat-websocket](chat-websocket.md) · [connection](connection.md) · [connection-websocket](connection-websocket.md) ·
[legal](legal.md) · [main](main.md) · [me](me.md) · [onboarding](onboarding.md) ·
[people](people.md) · [push](push.md) · [report](report.md) · [season](season.md)
