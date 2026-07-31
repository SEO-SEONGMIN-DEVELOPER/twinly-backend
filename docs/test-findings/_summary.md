# 테스트 발견사항 — 남은 할 일

REST 74개 오퍼레이션(springdoc `/v3/api-docs` 기준, springwolf 문서 3개 제외)과 WebSocket 채널에 대해
단위/슬라이스·통합 테스트를 작성하며 확인한 항목 중 **아직 처리하지 않은 것**의 인덱스다.

해결된 항목은 각 문서에서 제거한다. 해결 이력은 회귀 테스트와 커밋 메시지가 갖는다.

**버그가 아니라 아직 만들지 않은 기능**은 아래 "다음 개발 항목"으로 따로 뺐다. 고칠 코드가 있는 것과
새로 쓸 코드가 필요한 것은 판단 방식이 다르고, 섞어두면 남은 발견사항 수가 실제보다 부풀어 보인다.

## 현재 상태

| 태스크 | 테스트 수 | 실패 |
|---|---|---|
| `./gradlew test` (단위/슬라이스) | 429 | **0** |
| `./gradlew integrationTest` (통합, Testcontainers) | 144 | **0** |

통합 커버리지는 REST 74/74다.

> 통합 테스트는 로컬 `.env`의 암호화 키를 필요로 한다. 키가 없으면 `AesGcmEncryptor` 생성에 실패해
> 컨텍스트 기동 단계에서 전부 죽는다. CI에 붙일 때 시크릿 주입이 선행되어야 한다.

## 남은 항목 (2건)

| 문서 | 항목 | high | medium | low/info |
|---|---:|---:|---:|---:|
| [people](people.md) | 1 | 0 | 0 | 1 |
| [push](push.md) | 1 | 0 | 0 | 1 |
| **합계** | **2** | **0** | **0** | **2** |

둘 다 묶음에서 "지금은 미룬다"로 남긴 것이다. people은 그날 씬을 전부 로드해 메모리에서 거르는 성능 이슈(B6),
push는 기기 동시 등록의 check-then-act 경합(B12)이다. 트래픽이 붙기 전에는 체감되지 않는다.

## 다음 개발 항목 (발견사항 아님)

아래 11건은 **고칠 코드가 있는 게 아니라 새로 쓰거나 정책을 정해야 하는 것**이다. 무엇을 만들지가
제품·인프라 결정에 달려 있어 위 발견사항 수에서 뺐다.

### A. 매칭 성사 흐름 (chat-websocket 1·2, chat 6)

**채팅 도메인 전체가 데이터 진입점 없이 떠 있다.** 읽기·전송 API는 다 있는데 방을 만드는 코드가 없어,
지금 상태로는 어떤 유저도 채팅을 시작할 수 없다.

- `Match`에는 정적 팩토리조차 없다
- `ChatRoom.create`·`ChatRoomParticipation.create`는 엔티티 파일에만 있고 호출부가 없다
- 그 결과 `/app/chat/read`는 참여 행이 없어 운영에서 항상 실패한다
- `ChatChangedEvent` 발행처가 없어 `/user/queue/chat/index` 채널이 영구 무음이다 (리스너와 AsyncAPI 채널은 이미 있다)

정해야 할 것: 매칭 성사 기준과 시점 / 방을 성사 시점에 만들지 첫 메시지에 만들지 /
참여 행을 방과 함께 만들지 입장 시 만들지 / `ChatChangedEvent`를 어느 시점에 쏠지.

### B. 알림 피드 쓰기 경로 (main 2)

`AppNotificationFeed`를 저장하는 코드가 없다. `AppNotificationFeed.create`도 없고, 리포지토리 호출 5곳이
전부 조회·읽음처리다. 그래서 `unreadNotificationCount`는 항상 0이고 알림 목록도 항상 비어 있다.

정해야 할 것: 무엇이 알림이 되는가. A가 정해져야 매칭·채팅 관련 알림을 정의할 수 있다.

### C. WebSocket 운영 대응 (connection-websocket 1·2)

- `notifyDraining` 호출처가 없다. 배포 시 "곧 연결이 끊긴다"고 예고하는 용도라 **무중단 배포 절차가 정해져야** 붙일 수 있다
- 인메모리 `SimpleBroker` + 로컬 `SimpUserRegistry`라 인스턴스가 2대 이상이면 팬아웃이 절반만 나간다 (미검증). 배포 형태(단일 인스턴스 / 오토스케일)에 달렸다
- 팬아웃이 접속자 수만큼 호출 스레드에서 동기 루프로 돈다 (구 connection-websocket 3). **비동기로 돌리는 게 답이 아니라, 루프가 필요한 구조인지가 먼저다.** control 메시지는 모든 접속자에게 같은 내용이 가므로 개인 큐가 아니라 `/topic` 브로드캐스트가 맞을 수 있고, 그러면 루프 자체가 사라진다. 호출처가 없어 측정할 대상도 없다

셋 다 진행 중인 배포 작업과 함께 판단하는 것이 맞다.

### D. 남용 제어·정리 (auth 3·5·6, anon 1, connection 1)

전부 "무제한 반복 호출이 가능하다"는 성격이지만, **막을 수단이 배포 구성에 달려 있어** 지금 정할 수 없다.

- 인증번호 시도 횟수 제한 (brute force) — `auth 3`
- 발송 rate limit, 인증 세션 행 누적 — `auth 6`
- 인증 없는 공개 쓰기(익명 세션 발급)의 남용 제어 — `anon 1`
- `connection_tickets` 발급 제한·정리 배치 — `connection 1`

정해야 할 것: rate limit 수단. 오토스케일이면 인메모리(Bucket4j)가 무의미하고, 단일 인스턴스면 Redis는
과하다. 정리 배치도 어디서 돌릴지(앱 스케줄러 / 외부 크론)가 같은 판단에 걸린다.

`auth 5`(가입 여부가 응답으로 노출되어 계정 열거가 가능)는 성격이 다른 **제품 판단**이다. 미가입도
성공처럼 응답하면 열거를 막지만, 오타를 친 사용자가 "코드가 안 와요"로 헤맨다. 보안과 UX가 정면으로
부딪히므로 어느 쪽을 택할지 정해야 착수할 수 있다.

## 일괄 처리 묶음

도메인별로 하나씩 고치면 변경이 흩어지고 같은 판단을 여러 번 반복하게 된다.
**원인과 수정 방법이 같은 것끼리 묶어** 한 번에 처리한다. B15와 B8 잔여는 개발 항목으로 옮겼다. **묶음과 개별 항목이 모두 닫혔고, 남은 2건은 묶음에서 "지금은 미룬다"로 남긴 성능·경합 항목이다.**

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
| 익명 세션 만료 판정이 `expiresAt`과 같은 순간을 유효로 본다 (구 anon #3) | 마이크로초 경계라 재현이 불가능하고 의미 차이도 없다. 같은 패턴이 6곳(`AnonService`·`AuthService` 3·`CodeVerificationService`·`MeService`)에 **일관되게** 있어, 한 곳만 바꾸면 규칙이 갈린다. 일관성이 깨진 게 아니므로 두는 편이 낫다 |
| 차단 이력이 없어도 `DELETE /blocks/{userId}`가 200 (구 block #2) | B2 규칙("내 소유 행이 없으면 200")과 일치한다. 바로 위 `block()`도 이미 차단된 상대면 조용히 반환해 대칭이고, 계약은 `BlockIntegrationTest`로 고정돼 있다 |
| 멱등 재전송이 committed만 돌려주고 `ChatMessageCreatedEvent`를 다시 발행하지 않는다 (구 chat-websocket #5) | 원 문서의 근거("재발행하면 상대에게 중복 프레임")는 **틀렸다** — 페이로드의 `message.id`가 원본과 같아 `id`로 dedupe하면 무해하다. 진짜 근거는 **재발행이 반쪽짜리 복구**라는 점이다. 유실은 재연결 구간 전체에서 일어나는데 재전송으로 메워지는 건 자기가 보낸 1건뿐이라, 넣으면 "재전송하면 복구된다"는 오해만 만든다. 대신 그 사실과 dedupe 키를 AsyncAPI 채널 설명에 명시했다 |
| `INVALID_ANON_SESSION` 분기가 사실상 도달 불가 (구 onboarding #2) | 리졸버가 넘기는 것은 **값 객체**(`AnonSessionSnapshot`)인데 `basicInfo`는 엔티티를 수정해야 해 재조회가 필요하다. 같은 트랜잭션이라 1차 캐시에서 나오므로 쿼리도 두 번 나가지 않는다. 분기를 지우면 **다른 클래스(리졸버)의 런타임 동작에 기댄 가정**만 남아 조용히 깨진다. `AUTO_BLOCK`의 죽은 분기를 지운 것과 다른 이유가 여기 있다 — 그쪽은 같은 파일의 상수라 컴파일 타임에 불가능이 확정된다 |
| 기기 등록 응답으로 신규·갱신을 구분할 수 없다 (구 push #4) | 클라이언트가 이 구분을 쓸 화면이 없다. 그리고 이 API는 upsert라 호출이 무엇을 했는지 응답으로 단정할 수 없어, 같은 이유로 201도 주지 않았다. 필요해지는 시점(예: "새 기기 로그인" 알림)에는 응답 필드가 아니라 서버 알림이 답일 가능성이 크다 |
| 미등록·타인 `deviceId` 해제도 200 (구 push #5) | B2 규칙대로 내 소유 행이므로 200이고, 조회 조건에 `userId`가 들어가 남의 기기는 잡히지도 않는다. 로그아웃 시 호출되는 API라 재시도·중복 로그아웃이 흔해 멱등이 특히 중요하다. 관측용 `log.warn`도 붙이지 않았다 — 읽을 관측 체계가 없고 정상 흐름에서도 찍혀 노이즈가 된다 |
| 리소스를 생성하는 POST 다수가 여전히 200 (구 anon #2의 나머지) | 항상 새 리소스를 만드는 3개(`/anon/start`, `/auth/signup`, `/connection-tokens`)만 201로 바꿨다. 나머지 생성 POST는 전부 멱등이거나 upsert라 **아무것도 만들지 않았는데 201을 주면 응답이 거짓말**이 된다 |

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

### ~~B8. 남용 제어·rate limit (7건)~~ — 2건 처리, 5건은 개발 항목으로 이관

**한 묶음이 아니었다.** rate limit 수단이 필요한 것은 3건뿐이고 나머지는 각각 다른 문제였다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| connection | 4 | **로직 버그.** 스코프가 맞지 않으면 티켓을 소비하지 않고 반환해 같은 티켓으로 무제한 재시도할 수 있었다. 소비를 먼저 하고 스코프를 판정하도록 순서를 바꿨다 |
| push | 1 | **별도 작업에서 처리.** `deviceId`만으로 조회하던 것을 `(userId, deviceId)`로 좁혀 남의 기기 등록을 덮어쓸 수 없게 했다 |
| auth 3·5·6, anon 1, connection 1 | | **개발 항목 D로 이관.** 배포 구성·제품 판단이 선행되어야 한다 |

`connection 4`의 기존 테스트는 **결함을 정상 동작으로 고정하고 있었다**(`SCOPE_MISMATCH를 반환하고
소비하지 않는다`). 계약이 바뀐 것이라 테스트를 다시 썼다.

### ~~B9. WebSocket 에러·응답 처리 (4건)~~ — 처리 완료

| 문서 | 번호 | 처리 |
|---|---:|---|
| chat-websocket | 3 | HTTP status 대신 `ErrorCode` 자체로 `CommandErrorCode`를 정한다 |
| chat-websocket | 4 | 검증 실패도 목적지에 맞는 rejected 프레임으로 응답한다 |
| chat-websocket | 6 | **닫음.** `payloadType` 누락은 AsyncAPI 문서 품질 문제이고 `(미검증)`이다. 런타임 영향이 없다 |
| chat-websocket | 7 | **닫음.** `roomId`는 `Long`이라 URL 인코딩할 문자가 나올 수 없다. 무해하다 |

**에러 매핑.** status는 여러 원인의 묶음이라 1:1 대응이 성립하지 않았다. 404 하나에 `ROOM_NOT_FOUND`·
`MATCH_NOT_FOUND`·`CHAT_PARTICIPATION_NOT_FOUND` 셋이, 403에 `NOT_MATCH_PARTICIPANT`·
`NOT_ACTIVE_ROOM_PARTICIPANT` 둘이 걸려 있어 방이 멀쩡한데도 "존재하지 않는 채팅방입니다"를 받았다.
`CommandErrorCode`에 셋을 추가해 갈랐다. 422를 명령 종류별로 구분하려고 넘기던 `unprocessableCode`
파라미터는 필요 없어져 제거했다.

**검증 실패 응답.** 예외 핸들러가 로그만 남기고 끝나서, `@Valid` 실패나 봉투 검증 실패 시 클라이언트가
committed도 rejected도 받지 못했다. 통합 테스트로 실측해 확인한 사실이다(수신 프레임 0개).
"성공/실패 둘 중 하나는 반드시 돌려준다"는 명령-결과 프로토콜의 전제가 깨져 있었다.

`@MessageExceptionHandler`가 `Message<?>`와 `Principal`을 받으면 `userId`·`sessionId`가 나오고,
`message.getPayload()`가 **역직렬화 전 원본 JSON**이라 거기서 `commandId`를 꺼낼 수 있다. 즉 바디 검증이
실패해도 봉투의 `commandId`는 살아 있다. JSON 자체가 깨져 `commandId`를 못 꺼내는 경우에만 로그만 남긴다.

응답은 **새 봉투 타입을 만들지 않고 기존 `chat.message.rejected`·`chat.read.rejected`를 재사용**한다.
원본 `simpDestination`(`/app/chat/messages` vs `/app/chat/read`)으로 분기하면 되기 때문이다. 봉투의
`type` 필드는 검증 실패의 원인일 수 있어 신뢰할 수 없지만, `simpDestination`은 프레임워크가 채우므로
라우팅과 항상 일치한다. 전달 수단은 `ErrorCode.INVALID_REQUEST`라 기존 매핑 테이블에 한 줄만 더하면 된다.

응답 페이로드가 chat 전용이므로 **예외 핸들러도 chat에 둔다.** `common`에 두면 chat 목적지와 페이로드를
알아야 해서 `common`이 `chat`을 참조하지 않는 층 구분이 깨진다.

| 핸들러 | 범위 | 하는 일 |
|---|---|---|
| `GlobalWebSocketExceptionHandler` (common) | 모든 WS 컨트롤러 | 로그만. 예외가 세션을 끊지 않게 하는 최후 방어선 |
| `ChatWebSocketExceptionHandler` (chat) | `ChatWebSocketController`만 | 로그 + 거절 프레임 |

**둘 다 `Exception`을 잡고 chat 컨트롤러에는 둘 다 적용되므로 순서가 결과를 바꾼다.** `@Order` 없이는
빈 등록 순서(사실상 패키지 이름 알파벳 순)에 의존하게 되어, Global이 먼저 걸리면 예외를 삼켜 거절 프레임이
나가지 않는다. 실제로 순서를 뒤집어 통합 테스트가 깨지는 것을 확인했다. `@Order`로 "구체적인 advice가 먼저,
범용이 나중"을 명시했다.

`@ControllerAdvice`는 스코프를 지정해도 MVC 슬라이스 테스트(`@WebMvcTest`)에 로딩되는데 그 컨텍스트에는
`ChatCommandResponder` 빈이 없다. 14개 슬라이스 테스트를 고치는 대신 `ObjectProvider`로 받는다.

### ~~B10. 죽은 코드 (4건)~~ — 처리 완료

4건 중 **실제로 지울 수 있는 것은 2건뿐**이었다. 나머지 둘은 죽은 코드가 아니라 **미구현 기능**이라 B15로 옮겼다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| auth | 8 | `AuthLoginResult`·`AuthSignupResult`·`AuthRefreshResult` 삭제. 세 API 모두 실제로는 `AuthTokenResult`를 반환한다 |
| connection | 4 | `ConnectionService`의 미사용 `java.util.Optional` import 제거 |
| chat | 7 | **B15로 이관.** `ChatChangedEvent`는 발행처만 없을 뿐 리스너·AsyncAPI 채널이 이미 있다. 지우면 문서의 채널까지 없애야 하고, 프론트가 이미 구독 중일 수 있다 |
| main | 2 | **B15로 이관.** `unreadNotificationCount` 필드를 지우면 응답 스키마가 바뀐다. 알림 피드 쓰기 경로가 생기면 채워질 자리다 |

### ~~B12. 동시 요청 경합 (4건)~~ — 3건 처리, 1건 남김

네 곳 모두 `조회 → 없으면 생성 → 저장` 형태라 두 요청이 동시에 "없음"을 보면 둘 다 INSERT를 시도하고
유니크 제약 위반이 500으로 샜다.

**처음에 잡았던 방향(`DataIntegrityViolationException`을 catch 해서 멱등 처리)은 측정으로 뒤집혔다.**
예외를 삼켜도 JPA가 이미 트랜잭션을 rollback-only로 표시해서, 커밋 시점에 `UnexpectedRollbackException`이
난다. 500이 나는 것은 똑같고 예외 이름만 바뀐다. 통합 테스트로 확인했다.

그래서 `INSERT ... ON DUPLICATE KEY UPDATE`로 바꿨다. 원자적이라 경합 자체가 없어진다. 네 곳 모두
"있으면 값을 바꾸고 없으면 만든다"라 upsert의 의미와 정확히 일치하고, 바꾸는 값이 단일 플래그라
도메인 로직이 SQL로 흩어지는 부담도 작다.

| 문서 | 번호 | 처리 |
|---|---:|---|
| me | 2 | 푸시 알림 설정·프로필 공개 설정 upsert |
| season | 1 | 시즌 참가 upsert (`ON DUPLICATE KEY UPDATE id = id`로 no-op) |
| people | 1 | 즐겨찾기 upsert |
| push | 3 | **남김.** 소유자 검사가 들어가 upsert로 표현할 수 없다. `ON DUPLICATE KEY UPDATE`에는 조건을 붙일 수 없어, 남의 기기면 거절한다는 인가 규칙을 SQL로 옮기면 검사가 사라진다. 앱 시작 시 자동 호출이라 같은 유저가 동시에 부를 유인도 낮다 |

조회를 없앴으므로 재요청도 자연히 멱등하다. 시즌 참가는 통합 테스트에서 두 번 호출해 행이 하나로
유지되는 것을 고정했다.

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

### ~~개별 처리 (34건)~~ — 처리 완료

묶음에 들어가지 않는 항목들을 하나씩 검수했다. **고친 것 24건, 판단으로 닫은 것 8건, 개발 항목 이관 1건,
오진 1건.** 상세는 아래 7차 참조.

검수 방식은 앞선 라운드와 달랐다. **문서에 적힌 발견사항을 그대로 믿지 않고 매번 코드를 다시 읽었고**,
그 결과 문서가 낡았거나 근거가 틀린 것이 여러 건 나왔다. `(미검증)` 표시가 붙은 것은 실행해서 재현부터 확인했다.

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
| 만료 시각을 앱 시계로 저장하고 DB 시계로 판정해 TTL 60초가 9시간이 될 수 있다 (구 connection #1, 미검증) | **오진.** JVM을 Asia/Seoul, DB 세션을 `+09:00`으로 둔 상태에서 실측했더니 저장값은 UTC 벽시계 그대로였고 TTL도 60초였다. 운영 코드는 그대로 두고, 이 동작이 의도인지 우연인지가 코드에 드러나지 않아 경계 테스트로 남겼다 (`ConnectionIntegrationTest.expiresAt_is_stored_in_utc_regardless_of_db_session_time_zone`) |

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

### 7차 — 개별 항목 34건 검수 (2026-07-31 ~ 08-01)

묶음에 들어가지 않은 34건을 하나씩 검수했다. **문서를 믿지 않고 매번 코드를 다시 읽은 것**이 이 라운드의 방식이고,
그 덕에 문서가 낡았거나 근거가 틀린 것이 여러 건 드러났다.

| 내용 | 회귀 테스트 |
|---|---|
| 복구 후에도 `withdrawalScheduledAt`이 남아 상태 응답이 오염됨 | `MeServiceUnitTest.restore_clears_recoverable_until`, `MeIntegrationTest.restore_end_to_end` |
| 메인 배지가 채팅 목록의 가시성 규칙(`left_at`·`is_hidden`·차단)을 보지 않음 | `ChatIntegrationTest.unreadChatRoomCount_agrees_with_visible_rooms_in_list` |
| 친밀도 시계열 `from`/`to`가 오프셋을 무시해 하루가 밀림 | `PeopleControllerUnitTest`의 날짜 바인딩·400 케이스 |
| 온보딩 프로필이 비면 signup이 NPE로 500 | `AuthServiceUnitTest`·`AuthIntegrationTest`의 `PROFILE_NOT_COMPLETED` 케이스 |
| 참여 행이 없을 때 hide/leave만 500 (read/enter는 404) | `ChatServiceUnitTest`의 404 케이스 + 멱등 케이스 2건 |
| 같은 FCM 토큰이 여러 기기 행에 남아 중복 발송 | `PushServiceUnitTest`·`PushIntegrationTest`의 토큰 단일성 케이스 |
| 인증 완료 토큰이 30분간 재사용 가능하고 재확인으로 무한 연장됨 | `CodeVerificationServiceUnitTest` 2건 (TTL 5분, 재확인 시 토큰 유지) |
| 리프레시가 DB `expires_at`을 읽지 않음 | `AuthServiceUnitTest.refresh_with_expired_stored_token_throws` |
| 종료된 방에도 메시지가 저장됨 | `ChatServiceUnitTest.sendMessage_closed_room_throws`, WS 매핑 테스트 |
| 이벤트 목록이 관계 이력 전체를 매 요청 로딩 | `PeopleServiceUnitTest.events_delta_uses_relationship_before_page_range` |
| 정책 조회가 쓰지 않는 `content`(TEXT)까지 로딩 | 기존 legal·me 통합 테스트 (결과 불변 확인) |
| 404로 끝날 시계열 요청이 집계를 다 하고 실패 | `PeopleServiceUnitTest`의 조기 실패 단정 |
| `sent_at` 동률 2건이면 방 목록 전체가 500 | `ChatIntegrationTest.rooms_when_two_messages_share_sent_at_returns_latest_by_id` |
| 정책 목록 순서가 DB 반환 순서에 좌우됨 | `LegalIntegrationTest.policies_are_ordered_by_id_end_to_end` |
| 본문 길이 검사가 인가보다 먼저라 방 존재가 유추됨 | `ChatServiceUnitTest.sendMessage_over_limit_to_unknown_room_throws_room_not_found` |
| WEEK 버킷 대표값이 그 주 첫 값이라 그래프가 뒤처짐 | `PeopleServiceUnitTest`의 WEEK 케이스 (마지막 점 == `currentIntimacy`) |
| 요청 바디 enum만 대소문자를 구분 | `ConnectionIntegrationTest.token_with_lowercase_connection_type_end_to_end` |
| 읽음 처리 REST가 확정된 포인터를 버림 | `ChatIntegrationTest.readMessages_backwards_returns_confirmed_pointer` |
| 한 이벤트의 두 시각이 `Instant.now()` 두 번 호출로 어긋남 | `SeasonParticipationTest`·`ChatTest`·`AnonSessionPhotoTest` |
| 자기 호출이라 걸리지 않는 `@Transactional` | (동작 불변) `private`으로 내려 오해 경로 차단 |
| notifier만 `common.websocket`에 있어 의존 방향이 거꾸로 | (이동만) 기존 테스트 통과 |
| 차단 0건일 때도 조립을 수행 | `BlockIntegrationTest.blockList_when_no_block_returns_empty_list` |
| `AUTO_BLOCK` 상수가 만든 죽은 분기 | 기존 report 단위·통합 테스트 |
| 항상 새 리소스를 만드는 POST 3개가 200 | 각 컨트롤러·통합 테스트의 201 단정 |

**검수 중 새로 발견한 결함 2건**

| 내용 | 회귀 테스트 |
|---|---|
| `@ApiResponse`를 선언한 오퍼레이션 48개에서 성공(2xx) 응답이 문서에서 통째로 사라짐 | `ApiErrorContractIntegrationTest.api_docs_expose_success_response_for_every_operation` |
| AI 발화 신고가 **운영에서 항상 422** — 씬 소유자와 신고 대상을 비교하는데, 씬은 조회 API에서 항상 조회자 소유로만 내려간다 | `ReportServiceUnitTest` 3건, `ReportIntegrationTest.aiUtterance_success_end_to_end` |

앞의 것은 4차에서 오류 코드를 채우려고 단 애노테이션이 **성공 계약을 지운** 결과다. springdoc은 `@ApiResponse`가
있으면 **암묵적** 기본 성공 응답을 넣지 않는다(`@ResponseStatus`로 명시하면 넣는다). 48곳에 애노테이션을 다는
대신 커스터마이저 한 곳에서 채우도록 했다 — 그래야 "새로 `@ApiResponse`를 달 때 `@ResponseStatus`도 같이 달아야
한다"는 기억해야 할 규칙 자체가 없어진다.

뒤의 것은 **테스트가 잘못된 전제를 정답으로 고정**하고 있어 더 숨겨져 있었다. 통합 테스트 픽스처가 피신고자
소유의 씬을 직접 만들었는데, 그건 어떤 조회 API로도 만들어지지 않는 상태다. **픽스처를 조회 API가 실제로
만들어내는 상태로 구성해야** 이런 것이 드러난다.

**함께 처리한 것**: 트랜잭션 경계 기본값을 나머지 4개 서비스(`me`·`onboarding`·`block`·`activity`)로 넓혔다.
B7의 근거가 "누락이 반복된다"였는데 발견사항이 올라온 6개만 고쳐 두면 같은 함정이 남기 때문이다.
`readOnly`가 쓰기를 삼키면 예외 없이 UPDATE가 사라지므로, 정적 대조에 더해 `propagation = NOT_SUPPORTED`로
바깥 트랜잭션을 끈 프로브로 실제 반영을 확인했고, 쓰기 메서드의 애노테이션을 일부러 떼어 프로브가 유실을
잡는 것까지 확인한 뒤 지웠다.

## 도메인별 문서

[activity](activity.md) · [anon](anon.md) · [auth](auth.md) · [block](block.md) · [chat](chat.md) ·
[chat-websocket](chat-websocket.md) · [connection](connection.md) · [connection-websocket](connection-websocket.md) ·
[legal](legal.md) · [main](main.md) · [me](me.md) · [onboarding](onboarding.md) ·
[people](people.md) · [push](push.md) · [report](report.md) · [season](season.md)
