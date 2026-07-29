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

## 일괄 처리 묶음

108건을 도메인별로 하나씩 고치면 변경이 흩어지고 같은 판단을 여러 번 반복하게 된다.
**원인과 수정 방법이 같은 것끼리 묶어** 한 번에 처리한다. 아래 15개 묶음이 72건을 덮고, 나머지 36건은 개별 처리한다.

각 묶음은 커밋 하나 단위로 잡을 수 있게 나누었다. 우선순위는 위에서 아래 순이다.

### B1. 입력 검증 누락 (10건)

`@NotNull`만 있고 범위·형식·원소 제약이 없어, 400이어야 할 입력 오류가 500으로 나가거나 잘못된 값이 저장된다.
**요청 DTO에 제약 애노테이션을 붙이는 것으로 전부 해결된다.** 필수 파라미터 누락·검증 실패를 400으로 매핑하는
전역 핸들러는 이미 붙어 있으므로 추가 작업이 없다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat | 2 | `limit=0`이면 500, 음수면 SQL 오류 |
| chat | 6 | `text`에 `@NotBlank`/`@Size`가 없어 빈 메시지 저장 |
| me | 4 | `limit` 상한 없음 |
| me | 5 | `affiliation`이 공백 문자열도 통과 |
| onboarding | 3 | 관심사 원소가 null이면 500 |
| onboarding | 5 | 닉네임 길이·허용 문자 규칙 없음 |
| onboarding | 9 | 생년월일에 미래 날짜 허용 |
| people | 3 | `limit` 상한 없음 |
| people | 9 | `from > to` 미검증 |
| push | 4 | `deviceModel`/`fcmToken` 빈 문자열 저장 |

### B2. 존재하지 않는 대상 처리 통일 (7건)

"없는 대상"에 대한 응답이 API마다 다르다. 어떤 곳은 404, 어떤 곳은 조용히 200이다.
**규칙을 먼저 정하고 일괄 적용한다** — 조회·삭제는 멱등이므로 200, 생성·변경은 404가 기준선으로 적절하다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| block | 2 | 차단 대상 유저 존재 미검증 |
| people | 15 | 아무 userId나 200 |
| report | 3 | 신고 대상 유저 존재 미검증 |
| me | 3 | 없는 정책 버전 철회를 조용히 무시 (등록은 404) |
| onboarding | 7 | 위와 동일 |
| push | 7 | 남의 기기·미등록 기기 해제도 200 |
| season | 6 | 없는 시즌 id에 POST·GET 동작이 비일관 |

### B3. 정책 버전·발효 처리 (5건)

`PolicyCatalog`가 `effectiveAt` 조건 없이 모든 버전을 로드하는 데서 파생된다.
**카탈로그 한 곳을 고치면 대부분 함께 해결된다.**

| 문서 | 번호 | 내용 |
|---|---:|---|
| legal | 1 | 같은 `effectiveAt` 버전이 둘 이상이면 노출 버전이 비결정적 |
| legal | 2 | `policy_names.identifier`에 UNIQUE 없음 |
| legal | 4 | `effectiveAt`이 NULL이면 조용히 "버전 없음" |
| me | 2 | 미발효 버전에도 동의 가능 |
| onboarding | 6 | 시행 중인 버전인지 확인 안 함 |

### B4. 탈퇴·삭제 유저 표기 (5건)

탈퇴한 상대를 어떻게 보여줄지가 정해져 있지 않아 API마다 다르게 샌다.
**표기 규칙을 한 번 정하고 공통 변환 지점에 적용한다.**

| 문서 | 번호 | 내용 |
|---|---:|---|
| people | 1 | 탈퇴·차단 유저가 목록에 그대로 노출 |
| people | 2 | 상대 이름 표기 규칙이 API마다 다름 |
| people | 4 | 탈퇴한 상대의 실명 노출 |
| people | 5 | 이름이 null이면 `"nullnull"` |
| activity | 2 | 탈퇴·삭제된 동석자 이름이 null |

### B5. 시즌 설정 fail-fast (4건)

활성 시즌이 없거나 설정이 어긋나면 요청 시점에야 500으로 드러나고, 폭발 반경이 여러 화면에 걸친다.
**기동 시 검증 + 마이그레이션 시드 추가**로 한 번에 잡는다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| season | 2 | **high** — 활성 시즌 없으면 `CurrentSeasonReader` 경로 전부 500 |
| season | 4 | `currentSeasonId`가 `@Value` 필드 주입 |
| main | 1 | 시즌 길이가 0이면 0으로 나누기 → 메인 탭 전체 500 |
| main | 4 | 현재 시즌이 어긋나면 첫 화면 전체 500 |

### B6. 씬·JSON 파싱 처리 (6건)

씬(scene)의 `lines` JSON을 다루는 같은 코드 경로에서 파생된다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| activity | 1 | `parseLines` 파싱 실패가 500으로만 드러남 |
| people | 14 | `lines` JSON 손상 시 500 |
| activity | 3 | `version`을 첫 장면에서만 가져옴 |
| people | 17 | `version`을 필터된 첫 씬 값으로만 결정 |
| people | 13 | `scenesByDate.get(date).get(0)` NPE 가능 |
| people | 16 | 그날의 씬을 전부 로드 후 메모리 필터링 |

### B7. 트랜잭션 경계 (6건)

앞 넷은 `@Transactional(readOnly = true)` 누락이라 애노테이션 추가로 끝난다.
**뒤 둘은 쓰기 경로에 트랜잭션이 없어 실제 결함**이므로 함께 묶되 검증을 따로 한다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat | 10 | 조회 전용에 `readOnly` 없음 |
| legal | 6 | 위와 동일 |
| season | 5 | 위와 동일 |
| main | 5 | 조회 3건이 서로 다른 스냅샷에서 실행 |
| anon | 1 | 세션 발급에 서비스 계층 트랜잭션 경계 없음 |
| people | 6 | 조회-후-저장 경로에 트랜잭션 없음 |

### B8. 남용 제어·rate limit (7건)

전부 "무제한 반복 호출이 가능하다"는 같은 성격이다. 발송 비용·행 증가·brute force로 나타난다.
**공통 rate limit 수단을 하나 마련하면 적용은 기계적이다.**

| 문서 | 번호 | 내용 |
|---|---:|---|
| auth | 3 | 인증번호 시도 횟수 제한 없음 (brute force) |
| auth | 5 | 가입 여부가 응답으로 노출 (계정 열거) |
| auth | 6 | 발송 rate limit 없고 세션 행이 계속 쌓임 |
| anon | 2 | 인증 없는 공개 쓰기인데 남용 제어 없음 |
| connection | 1 | 티켓 발급 제한·정리 없어 테이블 무한 증가 |
| connection | 5 | `SCOPE_MISMATCH`일 때 티켓이 소비되지 않아 무제한 재시도 |
| push | 1 | `deviceId`만으로 소유자를 덮어써 기기 탈취 가능 |

### B9. WebSocket 에러·응답 처리 (4건)

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat-websocket | 3 | HTTP status 기반 매핑이라 원인이 뭉개짐 |
| chat-websocket | 4 | 검증 실패 시 클라이언트가 아무 응답도 못 받음 |
| chat-websocket | 6 | `@AsyncPublisher`에 `payloadType` 없음 (미검증) |
| chat-websocket | 7 | `roomId` URL 인코딩이 무의미 |

### B10. 죽은 코드 (4건)

| 문서 | 번호 | 내용 |
|---|---:|---|
| auth | 8 | 미사용 result DTO 3개 |
| connection | 4 | 사용하지 않는 import |
| main | 3 | `unreadNotificationCount`가 항상 0 (쓰기 경로 부재) |
| chat | 9 | `ChatChangedEvent` 죽은 알림 경로 (chat-websocket 1과 동일 원인) |

### B11. 중복 누적 (3건)

재제출 시 기존 행을 지우지 않고 append한다. **"치환"으로 바꿀지, 매번 새 행이 맞는지**를 항목별로 판단해야 한다.
신고는 "같은 사람을 다른 이유로 다시 신고"가 정상 흐름일 수 있어 제품 결정이 필요하다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| onboarding | 1 | 마지막 문항 재답변 시 페르소나 요소 중복 |
| onboarding | 4 | 관심사 재제출 시 중복 누적 |
| report | 2 | 같은 대상 반복 신고 시 매번 새 행 |

### B12. 동시 요청 경합 (3건)

check-then-act 사이에 다른 요청이 끼어들어 유니크 제약 위반이 500으로 샌다.
**우선순위는 낮다.** 같은 사용자의 재요청은 순차 멱등 처리로 이미 막히고, 남은 것들은 서로 다른 사용자가 겹칠
여지가 없는 자기 데이터 조작이다. 실제 발생 확률이 있는 닉네임 선점은 처리 완료.
트래픽이 붙은 뒤 5xx 지표에 `DataIntegrityViolationException`이 찍히면 그때 대응한다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| me | 6 | 알림·공개 설정 "조회 후 없으면 저장" |
| push | 3 | 기기 동시 등록 |
| season | 1 | 시즌 참가 check-then-act |

### B13. 네이티브 UPDATE 이후 stale (2건)

`@Modifying` 네이티브 쿼리 뒤 영속성 컨텍스트가 갱신되지 않는다. `clearAutomatically` 또는 재조회로 해결된다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat | 8 | 읽음 포인터 전진 후 stale |
| connection | 6 | 티켓 소비 후 stale |

### B14. DELETE 요청 바디 (2건)

RFC상 DELETE 바디는 의미가 정의되지 않아 일부 클라이언트·프록시가 제거한다. 두 곳을 같은 방식으로 바꾼다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| me | 7 | `DELETE /api/v1/me/consents` |
| onboarding | 8 | `DELETE /api/v1/onboarding/consents` |

### B15. 기능 미구현 — 제품 결정 선행 (4건)

버그 수정이 아니라 없는 기능을 만드는 일이다. **동작 정의가 먼저**다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat-websocket | 1 | **high** — `ChatChangedEvent` 발행처 없음 (목록 갱신 시점이 제품 결정) |
| chat-websocket | 2 | **high** — 방·참여 생성 흐름 자체가 없음 |
| connection-websocket | 1 | `notifyDraining` 호출처 없음 |
| connection-websocket | 2 | 인메모리 브로커라 다중 인스턴스에서 팬아웃 절반 유실 (미검증) |

### 개별 처리 (36건)

위 묶음에 들어가지 않는 항목들이다. 각 도메인 문서를 참조한다.
`(미검증)` 표시가 붙은 것은 **고치기 전에 재현부터 확인해야 한다.** 이번 라운드에서 high로 분류됐던
"빈 `IN` 절" 항목이 실제로는 결함이 아니었던 전례가 있다.

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
