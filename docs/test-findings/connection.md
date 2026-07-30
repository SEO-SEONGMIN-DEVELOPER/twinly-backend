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

### 1. 만료 판정에 '앱 시계'와 'DB 시계'를 섞어 쓴다 (타임존/시계 오차에 취약)
- 증상: 만료 시각은 애플리케이션이 `Instant.now().plus(60s)`로 계산해 저장하는데, 만료 여부 판정은 DB의 `UTC_TIMESTAMP(6)`와 `expires_at` 컬럼(`DATETIME(6)`, 타임존 정보 없음)을 비교해서 한다. 즉 쓰는 쪽과 읽는 쪽의 시계 기준이 다르다. JDBC/Hibernate의 `Instant` → `DATETIME(6)` 변환이 UTC가 아니라 JVM 기본 타임존(예: Asia/Seoul)으로 이뤄지면 `expires_at`이 UTC 기준 +9시간이 되어 TTL 60초가 사실상 9시간이 된다(반대 방향이면 발급 즉시 만료).
  - 운영 datasource URL은 `serverTimezone=UTC`를 명시하지만(`application.yaml`), `hibernate.jdbc.time_zone` 같은 명시 설정은 없어 동작이 인프라/드라이버 기본값에 의존한다. Testcontainers로 뜨는 통합 테스트 datasource URL에는 `serverTimezone` 파라미터 자체가 없다.
- 재현 조건: 앱 서버 JVM 타임존과 MySQL 세션 타임존이 다른 환경(로컬 Asia/Seoul + UTC DB 등)에서 티켓 발급 후 `ConnectionTicketRepository.consume`이 몇 행을 갱신하는지 확인.
- 근거 코드 위치:
  - `backend/src/main/java/com/nidus/twinly/connection/service/ConnectionService.java:28` (앱 시계로 expiresAt 계산)
  - `backend/src/main/java/com/nidus/twinly/connection/repository/ConnectionTicketRepository.java:17-24` (DB 시계 `UTC_TIMESTAMP(6)`와 비교)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:219` (`expires_at DATETIME(6)` — 타임존 무정보)
- 심각도: medium (환경 의존이라 실측 확인 필요 — 이번 통합 테스트는 발급 경로만 검증하고 consume 경로는 다루지 않아 이 항목을 판정하지 못한다)
- 제안: 시계 기준을 하나로 통일한다. 가장 작은 변경은 `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`를 명시해 저장 기준을 UTC로 못 박는 것이고, 근본적으로는 만료 비교도 `WHERE expires_at > :now` 처럼 애플리케이션이 넘긴 `Instant`로 하거나 컬럼을 `TIMESTAMP`로 바꾸는 방법이 있다.

### 2. 요청 본문의 enum은 대소문자를 구분해서, 다른 파라미터와 동작이 다르다
- 증상: `WebMvcConfig`가 `CaseInsensitiveEnumConverterFactory`를 `FormatterRegistry`에 등록해 두어 쿼리 파라미터·경로 변수의 enum은 대소문자 무시로 바인딩된다. 하지만 이 API의 `connectionType`은 `@RequestBody` JSON이라 Jackson이 처리하므로 대소문자를 구분한다. `{"connectionType":"ws"}`는 400이고 `?connectionType=ws`는 통과하는 식으로 API 표면에서 규칙이 갈린다.
- 재현 조건: `POST /api/v1/connection-tokens`에 `{"connectionType":"ws"}` 전송 → `HttpMessageNotReadableException` → 400 `INVALID_REQUEST`.
- 근거 코드 위치:
  - `backend/src/main/java/com/nidus/twinly/common/web/WebMvcConfig.java:26-29` (Formatter에만 등록)
  - `backend/src/main/java/com/nidus/twinly/connection/dto/request/ConnectionTokenRequest.java:6-9`
- 심각도: low
- 제안: 의도가 "enum은 대문자만 허용"이라면 그대로 두되 API 문서에 명시한다. 일관성을 원한다면 Jackson 쪽에도 `READ_ENUMS_USING_TO_STRING`/case-insensitive 설정을 맞춰 준다. 어느 쪽이든 정책을 한 번 정해서 문서화하는 것이 핵심이다.

## GET /ws/v1/ (핸드셰이크 — ConnectionService.resolveTicket 경유, 참고)

이 도메인 서비스의 나머지 public 메서드라 단위 테스트에 함께 포함했고, 그 과정에서 확인된 사항이다.

