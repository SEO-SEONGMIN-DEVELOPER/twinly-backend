# 테스트 발견사항 — 남은 할 일

REST 74개 오퍼레이션(springdoc `/v3/api-docs` 기준, springwolf 문서 3개 제외)과 WebSocket 채널에 대해
단위/슬라이스·통합 테스트를 작성하며 확인한 항목 중 **아직 처리하지 않은 것**의 인덱스다.

해결된 항목은 각 문서에서 제거한다. 해결 이력은 회귀 테스트와 커밋 메시지가 갖는다.

## 현재 상태

| 태스크 | 테스트 수 | 실패 |
|---|---|---|
| `./gradlew test` (단위/슬라이스) | 411 | **0** |
| `./gradlew integrationTest` (통합, Testcontainers) | 132 | **0** |

통합 커버리지는 REST 74/74다.

> 통합 테스트는 로컬 `.env`의 암호화 키를 필요로 한다. 키가 없으면 `AesGcmEncryptor` 생성에 실패해
> 컨텍스트 기동 단계에서 전부 죽는다. CI에 붙일 때 시크릿 주입이 선행되어야 한다.

## 남은 항목 (56건)

| 문서 | 항목 | high | medium | low/info |
|---|---:|---:|---:|---:|
| [auth](auth.md) | 7 | 0 | 3 | 4 |
| [chat-websocket](chat-websocket.md) | 7 | 2 | 2 | 3 |
| [chat](chat.md) | 6 | 0 | 3 | 3 |
| [people](people.md) | 6 | 0 | 3 | 3 |
| [push](push.md) | 5 | 0 | 2 | 3 |
| [connection-websocket](connection-websocket.md) | 4 | 0 | 2 | 2 |
| [connection](connection.md) | 4 | 0 | 2 | 2 |
| [anon](anon.md) | 3 | 0 | 1 | 2 |
| [block](block.md) | 2 | 0 | 0 | 2 |
| [legal](legal.md) | 2 | 0 | 0 | 2 |
| [main](main.md) | 2 | 0 | 2 | 0 |
| [me](me.md) | 2 | 0 | 1 | 1 |
| [onboarding](onboarding.md) | 2 | 0 | 0 | 2 |
| [report](report.md) | 2 | 0 | 0 | 2 |
| [season](season.md) | 2 | 0 | 1 | 1 |
| **합계** | **56** | **2** | **22** | **32** |

## 우선 처리 대상 — high 2건

| 도메인 | 내용 | 성격 |
|---|---|---|
| chat-websocket | `chat_room_participations` 행을 만드는 코드가 없어 `/app/chat/read`가 운영에서 항상 실패 | **기능 미구현.** 매칭 성사 시 방·참여를 만드는 흐름 자체가 없음 |
| chat-websocket | `ChatChangedEvent` 발행처가 없어 `/user/queue/chat/index` 채널이 영구 무음 | **기능 미구현.** 목록 갱신 시점이 제품 결정 사항 |

둘 다 버그 수정이 아니라 **없는 기능을 만드는 일**이다. "매칭 성사 시점에 방을 만들지, 첫 메시지에 만들지"가
정해져야 착수할 수 있다.

## 일괄 처리 묶음

도메인별로 하나씩 고치면 변경이 흩어지고 같은 판단을 여러 번 반복하게 된다.
**원인과 수정 방법이 같은 것끼리 묶어** 한 번에 처리한다. B1~B7·B10·B11·B13·B14가 닫혔고, 남은 묶음 4개가 21건을 덮는다.

각 묶음은 커밋 하나 단위로 잡을 수 있게 나누었다. 우선순위는 위에서 아래 순이다.

### ~~B1. 입력 검증 누락 (10건)~~ — 처리 완료

요청 DTO 제약, `limit` 범위, 닉네임 규칙, `from > to` 검증을 모두 적용했다. 상세는 아래 처리 완료 절 참조.

### 처리하지 않기로 판단한 항목

| 항목 | 판단 |
|---|---|
| 활성 시즌이 없으면 `CurrentSeasonReader`를 쓰는 5개 화면이 500 (구 season #2 / main #4, **high**) | 운영상 활성 시즌은 항상 존재한다. 기동 시 fail-fast는 마이그레이션 시드와 통합 테스트 픽스처 5개 클래스를 함께 바꿔야 해 비용이 크고, 막으려는 상황이 발생하지 않는다 |
| 시즌 길이가 0이면 메인 탭 500 (구 main #1) | 길이가 0인 시즌을 만들지 않는다 |
| 시행 중인 버전이 없는 약관은 `version`·`url`·`isRequired`가 `null`로 내려간다 (구 legal #4) | 현행 유지. 응답에서 빼면 "약관이 목록에 안 보인다"는 운영 사고가 조용히 묻히고, 500으로 막으면 약관 하나 때문에 온보딩 전체가 멈춘다. 동작은 `LegalServiceUnitTest.policies_excludes_versions_not_yet_effective`로 고정했다 |
| `@Modifying` 네이티브 UPDATE 후 영속성 컨텍스트가 stale (구 chat #6 / connection #5, **B13**) | 두 호출부 모두 UPDATE 뒤에 그 엔티티를 다시 읽지 않아 실제 오동작이 없다. 제안됐던 `flushAutomatically`는 **근거 자체가 틀렸다** — 측정해 보니 Hibernate가 네이티브 쿼리 실행 전에 이미 auto-flush 한다(`FlushMode.AUTO`는 네이티브 SQL을 해석하지 못해 전체를 flush). `clearAutomatically`는 컨텍스트를 통째로 비워, 이후 엔티티 수정이 **예외 없이 조용히 유실**될 수 있다. 보이는 문제(stale read)를 안 보이는 문제(lost update)로 바꾸는 거라 붙이지 않는다. 벌크 연산 뒤 같은 엔티티를 다시 읽는 곳(`AnonSessionPersonaElementRepository`)에만 붙어 있는 현 상태가 맞다 |
| 설문 **중간** 문항을 다시 답해도 페르소나가 갱신되지 않는다 | 페르소나 변환 트리거가 "마지막 문항"이라 중간 문항 재답변은 답변만 갱신되고 페르소나는 그대로다. 마지막 문항까지 다시 진행하면 차원 단위 치환으로 정합성이 회복되므로, 온보딩을 끝낸 사용자에게는 드러나지 않는다 |

### 사실이 아니게 된 항목 (코드가 이미 바뀜)

| 항목 | 확인 |
|---|---|
| `currentSeasonId`가 `@Value` 필드 주입 (구 season #4 / main #6) | `@Value`가 코드 전체에 없다. `CurrentSeasonReader`(활성 시즌 조회)로 대체됨 |
| 없는 시즌 id에 POST는 500, GET은 200으로 비일관 (구 season #6) | GET도 `CurrentSeasonReader.read()`를 거쳐 동작이 같아짐 |

### ~~B2. 존재하지 않는 대상 처리 통일 (6건)~~ — 처리 완료

규칙을 먼저 정하고 적용했다. **마스터 데이터(유저·정책)에 대상이 없으면 404, 내 소유 행이 없으면 200(멱등)** 이다.
"없는 유저를 차단/신고했다"는 클라이언트 버그를 조용히 삼키지 않으면서, 삭제·해제의 멱등성은 그대로 둔다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| block | 2 | 차단 대상 유저 존재 검증 → `USER_NOT_FOUND` |
| people | 13 | `event`도 `events`와 동일하게 상대 유저 검증 |
| me | 3 | 없는 정책 버전 철회 → `POLICY_NOT_FOUND` |
| onboarding | 5 | 위와 동일 |
| push | 5 | **규칙상 정상.** 기기는 마스터 데이터가 아니라 내 소유 행이므로 200을 유지한다 |
| report | 3 | **오분류.** 내용은 "`targetUserId`를 요청에서 받을 이유가 없다"는 API 정리 제안이다. report #2로 남겨둔다 |

### ~~B3. 정책 버전·발효 처리 (5건)~~ — 처리 완료

`PolicyCatalog`가 `effectiveAt`을 전혀 보지 않아 조회 API와 동의 API가 서로 다른 버전 집합을 쓰고 있었다.
**동의·철회 대상을 "이미 시행된 버전"으로 좁혔다.** 미시행(미래 시행일)과 초안(`effectiveAt IS NULL`)은 제외하되,
이미 시행됐던 구버전은 그대로 받는다. 앱 업데이트 전 구버전 클라이언트가 온보딩을 끝내지 못하는 상황을 막기 위해서다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| me | 2 | `PolicyCatalog.loadByKey`가 시행된 버전만 반환 (미래·초안 제외) |
| onboarding | 4 | 위와 동일 (같은 카탈로그를 공유) |
| legal | 1 | 동률 tie-break를 `version` 큰 쪽으로 고정 |
| legal | 2 | `identifier`를 `VARCHAR(100)`으로 좁히고 `uk_policy_names_identifier` UNIQUE 추가 |
| legal | 4 | **현행 유지.** 아래 "처리하지 않기로 판단한 항목" 참조 |

구버전 철회는 `revokeWithPreviousVersions...`가 `older.version <= target.version` 조건이라 그 버전과 이전 것들만
철회하고 이후 버전 동의는 남긴다. "v1을 철회해도 v2 동의는 유효"가 되므로 구버전을 받아도 의미가 어긋나지 않는다.

### ~~B4. 탈퇴·삭제 유저 표기 (5건)~~ — 처리 완료

**탈퇴는 가리고 남기며, 차단은 목록에서 뺀다**로 규칙을 갈랐다. 탈퇴는 "내 정보를 그만 보여달라"는 의사표시라
표기만 가리고 관계 기록은 남기고, 차단은 "앞으로 안 보겠다"는 뜻이라 진입점에서 제외하되 과거 기록은 건드리지 않는다.

| 상태 | 목록 | 과거 기록 | 표기 |
|---|---|---|---|
| 탈퇴 | 남김 | 남김 | 이름·닉네임·사진·공개 필드 마스킹 |
| 차단 | **제외** | 남김 | 그대로 |

| 문서 | 번호 | 처리 |
|---|---:|---|
| people | 1 | `GET /people`·`GET /chat/rooms`에서 차단 상대 제외. 탈퇴자는 남기고 마스킹 |
| people | 3 | `User.displayName()`으로 마스킹. 사진·`disclosedFields`는 조회 자체를 건너뜀 |
| activity | 2 | 동석자 이름이 `null` 대신 `"탈퇴한 사용자"`로 수렴 |
| people | 2 | **의도된 동작.** 목록만 이름, 나머지는 성+이름인 것은 화면별 의도라 유지 |
| people | 4 | **도달 불가.** `users.family_name`·`given_name`이 `NOT NULL`이라 `"nullnull"`이 생기지 않음 |

표기 규칙은 `User`에 모았다(`displayName`·`displayGivenName`·`displayNickname`·`isWithdrawn`).
호출부의 `user != null` 가드는 제거했다 — `users`를 참조하는 FK가 33개라 참조가 남은 행은 삭제되지 않고,
그 상황이 실제로 생긴다면 탈퇴가 아니라 정합성 붕괴이므로 `"탈퇴한 사용자"`로 덮기보다 NPE로 드러나는 편이 낫다.

차단 제외는 **SQL에서 처리했다.** 조회 후 애플리케이션에서 걸러내면 `LIMIT :limit + 1`로 `hasMore`를 판정하는
커서 페이지네이션이 깨진다(20건을 가져와 15건이 차단이면 5건만 남고 다음 페이지 유무도 어긋난다).

**매칭 후보에서의 차단 제외는 보류했다.** `Match`를 생성하는 코드 자체가 아직 없다(B15).
`deleted_at`을 채우는 배치도 아직 없어, 탈퇴 마스킹은 그 배치가 생기는 시점에 발현된다.

### ~~B6. 씬·JSON 파싱 처리 (6건)~~ — 처리 완료

**6건 중 실제로 고친 것은 2건이다.** 나머지는 불변식이 확인되었거나(2건), 도달 경로가 닫혔거나(1건), 성능이라 미뤘다(1건).

| 문서 | 번호 | 처리 |
|---|---:|---|
| activity | 1 | 파싱 실패 시 그 씬의 대사만 빈 목록으로 대체하고 `log.warn`. 하루치 조회가 통째로 500이 되지 않는다 |
| people | 8 | 위와 동일. `preview`만 `null`로 떨어진다 |
| activity | 2 | **불변식 확인.** 같은 날짜의 씬은 같은 `version`을 갖는 것이 보장되므로 첫 씬 값을 대표로 쓰는 현행이 맞다 |
| people | 10 | 〃 |
| people | 7 | **도달 불가.** B7로 `events()`가 `readOnly` 트랜잭션이 되어 두 쿼리가 같은 스냅샷을 본다. NPE 경로가 닫혔다 |
| people | 9 | **미룸.** 그날 씬 전부를 로드해 메모리에서 거르는 성능 이슈. 하루 씬이 수십 개가 되기 전에는 체감되지 않는다. 개별 항목으로 남긴다 |

`parseLines`는 두 서비스에 복제돼 있어 양쪽에 같은 처리를 넣었다. 잡는 예외는 `RuntimeException`이 아니라
`tools.jackson.core.JacksonException`이다 — 전자로 잡으면 파싱과 무관한 버그까지 삼킨다.
로그에는 `sceneId`를 넣었다. 그게 없으면 손상된 행을 찾을 수 없다.

**`log.warn`인 이유**: 요청은 200으로 성공한다. 실패가 아니므로 즉시 대응 대상(`ERROR`)이 아니고,
누적되면 파이프라인 이상 신호이므로 조용히 넘겨서도 안 된다.

### ~~B7. 트랜잭션 경계 (6건)~~ — 처리 완료

6곳에 애노테이션을 붙이는 대신 **기본값을 정했다.** 6개 서비스 클래스에
`@Transactional(readOnly = true)`를 걸고, 쓰기 메서드만 `@Transactional`로 덮어쓴다.
메서드마다 판단해서 붙이면 조회 메서드가 늘어날 때마다 같은 누락이 반복된다 — 이 6건이 그 결과였다.

`readOnly = true`를 세트로 붙인 이유는 성능이 아니다. 조회에 `@Transactional`만 걸면
영속성 컨텍스트가 메서드 끝까지 살아 있어, 조립 중 엔티티를 건드리면 **`save()` 없이 UPDATE가 나간다.**
`readOnly = true`는 flush 자체를 막아 그 부작용 없이 스냅샷 일관성만 가져온다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| chat | 8 | 클래스 레벨 `readOnly`. `rooms`의 조회 9건이 한 스냅샷·한 커넥션으로 수렴 |
| legal | 3 | 위와 동일 |
| season | 3 | 위와 동일. 단건 조회라 실효는 없고 경계 일관성 목적 |
| main | 3 | 위와 동일. 배지 3개가 같은 시점 기준이 됨 |
| anon | 1 | 클래스 레벨 `readOnly` + `start()`에 `@Transactional` |
| people | 1 | `favorite`·`deleteFavorite`에 `@Transactional`. **경합은 미해결 — B12로 이관** |

`readOnly` 누락은 **통합 테스트가 잡아주지 못한다.** `AbstractIntegrationTest`가 클래스 레벨
`@Transactional`이라 서비스가 바깥 트랜잭션에 참여하고, 참여하는 트랜잭션의 `readOnly`는 무시되기 때문이다.
그래서 검증은 (a) 쓰기 호출부 6곳이 전부 `@Transactional` 메서드 안에 있는지 정적 대조,
(b) 바깥 트랜잭션 없이 도는 `ChatWebSocketIntegrationTest`로 `sendMessage`·`readMessages`의 실제 DB 반영 확인,
두 가지로 했다. 서비스가 더 늘어 (a)를 눈으로 하기 버거워지면 ArchUnit 규칙으로 옮긴다.

### B8. 남용 제어·rate limit (7건)

전부 "무제한 반복 호출이 가능하다"는 같은 성격이다. 발송 비용·행 증가·brute force로 나타난다.
**공통 rate limit 수단을 하나 마련하면 적용은 기계적이다.**

| 문서 | 번호 | 내용 |
|---|---:|---|
| auth | 3 | 인증번호 시도 횟수 제한 없음 (brute force) |
| auth | 5 | 가입 여부가 응답으로 노출 (계정 열거) |
| auth | 6 | 발송 rate limit 없고 세션 행이 계속 쌓임 |
| anon | 1 | 인증 없는 공개 쓰기인데 남용 제어 없음 |
| connection | 1 | 티켓 발급 제한·정리 없어 테이블 무한 증가 |
| connection | 4 | `SCOPE_MISMATCH`일 때 티켓이 소비되지 않아 무제한 재시도 |
| push | 1 | `deviceId`만으로 소유자를 덮어써 기기 탈취 가능 |

### B9. WebSocket 에러·응답 처리 (4건)

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat-websocket | 3 | HTTP status 기반 매핑이라 원인이 뭉개짐 |
| chat-websocket | 4 | 검증 실패 시 클라이언트가 아무 응답도 못 받음 |
| chat-websocket | 6 | `@AsyncPublisher`에 `payloadType` 없음 (미검증) |
| chat-websocket | 7 | `roomId` URL 인코딩이 무의미 |

### ~~B10. 죽은 코드 (4건)~~ — 처리 완료

4건 중 **실제로 지울 수 있는 것은 2건뿐**이었다. 나머지 둘은 죽은 코드가 아니라 **미구현 기능**이라 B15로 옮겼다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| auth | 8 | `AuthLoginResult`·`AuthSignupResult`·`AuthRefreshResult` 삭제. 세 API 모두 실제로는 `AuthTokenResult`를 반환한다 |
| connection | 4 | `ConnectionService`의 미사용 `java.util.Optional` import 제거 |
| chat | 7 | **B15로 이관.** `ChatChangedEvent`는 발행처만 없을 뿐 리스너·AsyncAPI 채널이 이미 있다. 지우면 문서의 채널까지 없애야 하고, 프론트가 이미 구독 중일 수 있다 |
| main | 2 | **B15로 이관.** `unreadNotificationCount` 필드를 지우면 응답 스키마가 바뀐다. 알림 피드 쓰기 경로가 생기면 채워질 자리다 |

### B12. 동시 요청 경합 (4건)

check-then-act 사이에 다른 요청이 끼어들어 유니크 제약 위반이 500으로 샌다.
**우선순위는 낮다.** 같은 사용자의 재요청은 순차 멱등 처리로 이미 막히고, 남은 것들은 서로 다른 사용자가 겹칠
여지가 없는 자기 데이터 조작이다. 실제 발생 확률이 있는 닉네임 선점은 처리 완료.
트래픽이 붙은 뒤 5xx 지표에 `DataIntegrityViolationException`이 찍히면 그때 대응한다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| me | 2 | 알림·공개 설정 "조회 후 없으면 저장" |
| push | 3 | 기기 동시 등록 |
| season | 1 | 시즌 참가 check-then-act |
| people | 1 | 즐겨찾기 "조회 후 없으면 생성" (B7에서 이관. 트랜잭션 경계는 붙었으나 경합은 그대로) |

### ~~B13. 네이티브 UPDATE 이후 stale (2건)~~ — 처리하지 않음

**실제 오동작이 없고, 제안됐던 수정의 근거가 측정으로 뒤집혔다.** 아래 "처리하지 않기로 판단한 항목" 참조.

### ~~B14. DELETE 요청 바디 (2건)~~ — 처리 완료

바디가 필수인데 메서드가 `DELETE`라, 중간 프록시·CDN이나 일부 클라이언트가 바디를 버리면
`HttpMessageNotReadableException` → 400이 된다. 로컬에서는 재현되지 않고 운영에서만 터지는 종류다.
철회 대상이 `(policyId, version)` **쌍의 목록**이라 쿼리 파라미터로 옮기면 인덱스 대응 규약을
클라이언트에 전가하게 되므로, **바디 사용이 스펙상 정당한 `POST` 서브리소스로 이동**했다.
DTO·Command·서비스 계층은 그대로다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| me | 5 | `DELETE /api/v1/me/consents` → `POST /api/v1/me/consents/revoke` |
| onboarding | 6 | `DELETE /api/v1/onboarding/consents` → `POST /api/v1/onboarding/consents/revoke` |

### B15. 기능 미구현 — 제품 결정 선행 (6건)

버그 수정이 아니라 없는 기능을 만드는 일이다. **동작 정의가 먼저**다.

| 문서 | 번호 | 내용 |
|---|---:|---|
| chat-websocket | 1 | **high** — `ChatChangedEvent` 발행처 없음 (목록 갱신 시점이 제품 결정) |
| chat-websocket | 2 | **high** — 방·참여 생성 흐름 자체가 없음 |
| chat | 6 | 위 chat-websocket 1과 같은 원인. 리스너·채널은 있고 발행만 없다 |
| main | 2 | `unreadNotificationCount`가 항상 0 — 알림 피드 쓰기 경로 부재 |
| connection-websocket | 1 | `notifyDraining` 호출처 없음 |
| connection-websocket | 2 | 인메모리 브로커라 다중 인스턴스에서 팬아웃 절반 유실 (미검증) |

### 개별 처리 (35건)

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

### 4차 — 입력 검증과 오류 응답 계약 (2026-07-29)

B1(입력 검증 10건)을 처리하면서, 오류 응답이 `ErrorCode` 규약을 벗어나는 지점들도 함께 정리했다.

| 내용 | 회귀 테스트 |
|---|---|
| 요청 DTO·`limit`·닉네임·`from > to` 제약 부재로 400이어야 할 것이 500이거나 잘못된 값이 저장됨 | 각 컨트롤러 슬라이스 테스트의 `..._returns_400` 케이스 |
| 존재하지 않는 경로·지원하지 않는 메서드가 500 (봇 스캔이 5xx 지표를 오염) | `ApiErrorContractIntegrationTest.unknown_path_returns_404`, `unsupported_method_returns_405` |
| `ResponseStatusException` 경로의 `code`가 `null` | 〃 |
| 커스텀 메시지가 응답으로 새어 내부 값(S3 key, contentType) 노출 | — (`ErrorResponse.of(ErrorCode, String)` 제거로 경로 자체를 없앰) |
| `VERIFICATION_NOT_COMPLETED` 하나로 SMS/EMAIL 두 상황을 표현 | `AuthServiceUnitTest`의 SMS/EMAIL 각 케이스 |
| 두 `ErrorCode`가 같은 기본 문구를 사용 | `ErrorCodeTest.default_messages_are_distinct` |
| OpenAPI의 400·500이 springdoc 기본값, 401이 대표값 하나뿐, 던져지는 코드 7개가 문서에 없음 | `ApiErrorContractIntegrationTest` 4건 |

### 5차 — 존재 검증·정책 버전 (2026-07-30)

B2·B3·B11을 처리했다. 셋 다 "규칙이 정해져 있지 않아 API마다 다르게 샜다"는 같은 성격이다.

| 내용 | 회귀 테스트 |
|---|---|
| 없는 유저를 차단/조회해도 200이라 클라이언트 버그가 묻힘 | `BlockServiceUnitTest.block_unknown_user_throws`, `PeopleServiceUnitTest.event_user_not_found` |
| 없는 정책 버전 철회가 200 (동의는 404라 비대칭) | `MeServiceUnitTest`·`OnboardingServiceUnitTest`의 `revokeConsents_unknown_policy_throws` |
| 조회 API에 안 보이는 미시행 버전·초안으로도 동의가 저장됨 | `PolicyCatalogUnitTest` 3건 (시행된 버전 전체·미래 제외·시행일 없음 제외) |
| 시행일이 같은 버전이 둘이면 목록에 뜨는 버전이 DB 반환 순서에 좌우됨 | `LegalServiceUnitTest.policies_breaks_effective_at_tie_by_version` |
| `policy_names.identifier` 중복 시 `Collectors.toMap`이 500 | `uk_policy_names_identifier` (DB 제약으로 차단) |
| 설문·관심사 재제출과 반복 신고에서 행이 누적됨 | `OnboardingServiceUnitTest`의 치환 케이스, `ReportIntegrationTest.reportUser_same_content_is_idempotent` |

`identifier`는 `TEXT`에서 `VARCHAR(100)`으로 좁혔다. MySQL은 접두어 길이 없이 `TEXT`에 UNIQUE를 걸 수 없고,
`terms_of_service` 같은 고정 기계 키에 `TEXT`는 애초에 과했다. **V1 수정이므로 로컬 DB 볼륨 재생성이 필요하다.**

### 6차 — 동의 철회 엔드포인트 이전 (2026-07-30)

| 내용 | 회귀 테스트 |
|---|---|
| 동의 철회가 `DELETE` + 필수 바디라, 바디를 버리는 프록시·클라이언트를 거치면 400 | `MeControllerUnitTest.revokeConsents_success`, `OnboardingControllerUnitTest.revokeConsents_success`, 두 도메인 `IntegrationTest`의 철회 케이스 |

**호환성이 깨지는 변경이다.** 경로와 메서드가 함께 바뀌므로 클라이언트가 동시에 나가야 한다.
구 경로를 한동안 남겨 두는 선택지는 취하지 않았다. 아직 클라이언트 배포 전이라 이중 유지 비용이 이득보다 크다.

## 도메인별 문서

[activity](activity.md) · [anon](anon.md) · [auth](auth.md) · [block](block.md) · [chat](chat.md) ·
[chat-websocket](chat-websocket.md) · [connection](connection.md) · [connection-websocket](connection-websocket.md) ·
[legal](legal.md) · [main](main.md) · [me](me.md) · [onboarding](onboarding.md) ·
[people](people.md) · [push](push.md) · [report](report.md) · [season](season.md)
