# connection 도메인 테스트 발견사항


## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`ConnectionControllerUnitTest` 4 + `ConnectionServiceUnitTest` 7) | **11/11 통과** |
| 통합 (`ConnectionIntegrationTest`) | **3/3 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## POST /api/v1/connection-tokens

### 1. 발급 제한·정리(cleanup)가 전혀 없어 connection_tickets 테이블이 무한 증가한다
- 증상: 인증만 통과하면 호출할 때마다 새 티켓 행이 무조건 INSERT 된다. 발급 rate limit도, 사용/만료된 티켓을 삭제하는 스케줄러·배치도 코드 전체에 존재하지 않는다(`@Scheduled` / `deleteBy*` 검색 결과 0건). 티켓 TTL이 60초라 실제로 유효한 행은 극소수인데, 행은 영구히 남는다.
- 재현 조건: 로그인한 유저가 `POST /api/v1/connection-tokens`을 반복 호출. N번 호출 → `connection_tickets` N행. 재연결이 잦은 모바일 클라이언트에선 유저당 하루 수백 행이 쌓인다.
- 근거 코드 위치:
  - `backend/src/main/java/com/nidus/twinly/connection/service/ConnectionService.java:27-34` (조건 없이 save)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:214-226` (정리 대상 테이블, TTL 컬럼만 있고 정리 주체 없음)
- 심각도: medium
- 제안: (a) 만료 후 일정 기간이 지난 행을 지우는 정리 작업(스케줄러 또는 DB 이벤트)을 추가하거나, (b) `user_id + connection_type` 단위로 유효 티켓을 1건만 유지(upsert)하도록 바꾼다. 지금 당장은 (a)가 변경 범위가 작다.

### 2. 만료 판정에 '앱 시계'와 'DB 시계'를 섞어 쓴다 (타임존/시계 오차에 취약)
- 증상: 만료 시각은 애플리케이션이 `Instant.now().plus(60s)`로 계산해 저장하는데, 만료 여부 판정은 DB의 `UTC_TIMESTAMP(6)`와 `expires_at` 컬럼(`DATETIME(6)`, 타임존 정보 없음)을 비교해서 한다. 즉 쓰는 쪽과 읽는 쪽의 시계 기준이 다르다. JDBC/Hibernate의 `Instant` → `DATETIME(6)` 변환이 UTC가 아니라 JVM 기본 타임존(예: Asia/Seoul)으로 이뤄지면 `expires_at`이 UTC 기준 +9시간이 되어 TTL 60초가 사실상 9시간이 된다(반대 방향이면 발급 즉시 만료).
  - 운영 datasource URL은 `serverTimezone=UTC`를 명시하지만(`application.yaml`), `hibernate.jdbc.time_zone` 같은 명시 설정은 없어 동작이 인프라/드라이버 기본값에 의존한다. Testcontainers로 뜨는 통합 테스트 datasource URL에는 `serverTimezone` 파라미터 자체가 없다.
- 재현 조건: 앱 서버 JVM 타임존과 MySQL 세션 타임존이 다른 환경(로컬 Asia/Seoul + UTC DB 등)에서 티켓 발급 후 `ConnectionTicketRepository.consume`이 몇 행을 갱신하는지 확인.
- 근거 코드 위치:
  - `backend/src/main/java/com/nidus/twinly/connection/service/ConnectionService.java:28` (앱 시계로 expiresAt 계산)
  - `backend/src/main/java/com/nidus/twinly/connection/repository/ConnectionTicketRepository.java:17-24` (DB 시계 `UTC_TIMESTAMP(6)`와 비교)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:219` (`expires_at DATETIME(6)` — 타임존 무정보)
- 심각도: medium (환경 의존이라 실측 확인 필요 — 이번 통합 테스트는 발급 경로만 검증하고 consume 경로는 다루지 않아 이 항목을 판정하지 못한다)
- 제안: 시계 기준을 하나로 통일한다. 가장 작은 변경은 `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`를 명시해 저장 기준을 UTC로 못 박는 것이고, 근본적으로는 만료 비교도 `WHERE expires_at > :now` 처럼 애플리케이션이 넘긴 `Instant`로 하거나 컬럼을 `TIMESTAMP`로 바꾸는 방법이 있다.

### 3. 요청 본문의 enum은 대소문자를 구분해서, 다른 파라미터와 동작이 다르다
- 증상: `WebMvcConfig`가 `CaseInsensitiveEnumConverterFactory`를 `FormatterRegistry`에 등록해 두어 쿼리 파라미터·경로 변수의 enum은 대소문자 무시로 바인딩된다. 하지만 이 API의 `connectionType`은 `@RequestBody` JSON이라 Jackson이 처리하므로 대소문자를 구분한다. `{"connectionType":"ws"}`는 400이고 `?connectionType=ws`는 통과하는 식으로 API 표면에서 규칙이 갈린다.
- 재현 조건: `POST /api/v1/connection-tokens`에 `{"connectionType":"ws"}` 전송 → `HttpMessageNotReadableException` → 400 `INVALID_REQUEST`.
- 근거 코드 위치:
  - `backend/src/main/java/com/nidus/twinly/common/web/WebMvcConfig.java:26-29` (Formatter에만 등록)
  - `backend/src/main/java/com/nidus/twinly/connection/dto/request/ConnectionTokenRequest.java:6-9`
- 심각도: low
- 제안: 의도가 "enum은 대문자만 허용"이라면 그대로 두되 API 문서에 명시한다. 일관성을 원한다면 Jackson 쪽에도 `READ_ENUMS_USING_TO_STRING`/case-insensitive 설정을 맞춰 준다. 어느 쪽이든 정책을 한 번 정해서 문서화하는 것이 핵심이다.

### 4. 사용하지 않는 import
- 증상: `ConnectionService`가 `java.util.Optional`을 import 하지만 사용하지 않는다(`findByTicket(...).orElse(null)`로만 소비).
- 재현 조건: 정적 분석/IDE 경고.
- 근거 코드 위치: `backend/src/main/java/com/nidus/twinly/connection/service/ConnectionService.java:15`
- 심각도: low
- 제안: import 제거.

## GET /ws/v1/ (핸드셰이크 — ConnectionService.resolveTicket 경유, 참고)

이 도메인 서비스의 나머지 public 메서드라 단위 테스트에 함께 포함했고, 그 과정에서 확인된 사항이다.

### 5. SCOPE_MISMATCH일 때 티켓이 소비되지 않아 무제한 재시도가 가능하다
- 증상: `resolveTicket`은 `connectionType` 불일치를 소비(consume)보다 먼저 검사하고 그대로 반환한다. 따라서 잘못된 스코프로 시도한 티켓은 소진되지 않고 TTL 60초 동안 살아남아 계속 재시도할 수 있다.
- 재현 조건: SSE용 티켓을 발급받아 `/ws/v1/?ticket=...`로 반복 접속 시도 → 매번 403, 티켓은 계속 유효.
- 근거 코드 위치: `backend/src/main/java/com/nidus/twinly/connection/service/ConnectionService.java:44-50`
- 심각도: low (티켓 값은 소유자만 알고 TTL이 60초라 실질 위험은 작다. 다만 "1회용 티켓"이라는 의도와는 어긋난다)
- 제안: 스코프 불일치도 소비 후 실패로 처리할지 정책을 명시한다. 현재 동작이 의도라면 주석으로 이유를 남긴다.

### 6. 네이티브 `@Modifying` 쿼리 이후 영속성 컨텍스트가 stale 하다
- 증상: `consume`은 `nativeQuery = true`의 `@Modifying`인데 `clearAutomatically`/`flushAutomatically`가 없다. 같은 트랜잭션에서 앞서 `findByTicket`으로 로딩한 `ConnectionTicket` 엔티티는 `usedAt`이 여전히 `null`인 채로 1차 캐시에 남는다.
- 재현 조건: 같은 트랜잭션 안에서 `resolveTicket` 이후 동일 티켓을 다시 조회하면 `usedAt`이 null로 보인다. (현재 호출부는 그 뒤로 엔티티를 다시 읽지 않아 실제 오동작은 없다 — 잠재 함정)
- 근거 코드 위치: `backend/src/main/java/com/nidus/twinly/connection/repository/ConnectionTicketRepository.java:16-24`
- 심각도: low
- 제안: 지금은 문제가 없으므로 그대로 두되, 이후 `resolveTicket` 뒤에 티켓을 다시 읽는 로직이 생기면 `@Modifying(clearAutomatically = true)`를 붙인다.
