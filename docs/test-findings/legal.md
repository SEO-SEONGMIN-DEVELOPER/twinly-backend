# legal 도메인 테스트 발견사항


## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`LegalControllerUnitTest` 3 + `LegalServiceUnitTest` 3) | **6/6 통과** |
| 통합 (`LegalIntegrationTest`) | **2/2 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## GET /api/v1/legal/policies

### 1. 같은 정책명에 동일한 `effectiveAt` 버전이 둘 이상이면 노출 버전이 비결정적이다

- **증상**: 최신 시행 버전을 고르는 merge 함수가 `a.getEffectiveAt().isAfter(b.getEffectiveAt()) ? a : b` 라서
  두 버전의 `effectiveAt`이 **완전히 같으면 항상 뒤에 온 쪽(b)** 이 선택된다.
  그런데 `findAllByPolicyNameIdIn`에는 `ORDER BY`가 없으므로 "뒤에 온 쪽"은 DB가 돌려주는 순서에 달려 있다.
  결과적으로 어떤 version/url/isRequired가 클라이언트에 내려갈지 예측할 수 없다.
- **재현 조건**: 동일 `policy_name_id`에 대해 `effective_at`이 같은 두 행(예: v2, v3)을 넣고 API를 호출한다.
  (`uk_policies_policy_name_id_version`은 version만 막으므로 같은 시행일 자체는 막지 못한다)
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:35`
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:30`
- **심각도**: medium
- **제안**: 동점일 때 `version`이 큰 쪽을 고르도록 tie-break를 넣는다.
  ```java
  Comparator<Policy> latest = Comparator
          .comparing(Policy::getEffectiveAt)
          .thenComparing(Policy::getVersion);
  ... (a, b) -> latest.compare(a, b) >= 0 ? a : b
  ```

### 2. `policy_names.identifier`에 UNIQUE 제약이 없다

- **증상**: `identifier`가 `TEXT NOT NULL`일 뿐 유니크 제약이 없다.
  운영 실수로 같은 `identifier`가 두 행 들어가면 이 API는 같은 `policyId`를 가진 항목을 **중복으로** 내려준다.
  더 나쁜 것은 같은 legal 패키지의 `PolicyCatalog.loadByKey`가 `(identifier, version)`을 키로 `Collectors.toMap`을 쓰기 때문에,
  중복 identifier + 같은 version 조합이 생기면 **`IllegalStateException: Duplicate key`로 온보딩/약관 동의 API가 500으로 죽는다.**
- **재현 조건**: `policy_names`에 `identifier = 'terms_of_service'` 인 행을 2건 넣고, 각각에 같은 version의 policy를 넣은 뒤
  `PolicyCatalog.loadByKey(List.of("terms_of_service"))`를 타는 API를 호출한다.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/resources/db/migration/V1__init_schema.sql:374`
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/PolicyCatalog.java:28`
- **심각도**: medium
- **제안**: `identifier`를 `VARCHAR(n)`로 좁히고 `UNIQUE` 제약을 건다. (TEXT는 prefix 길이 없이는 인덱싱도 불가)

### 3. 응답 배열의 순서가 보장되지 않는다

- **증상**: `findAllByIsDeprecatedFalse()`에 정렬 기준이 없어 응답 `policies` 배열의 순서가 DB 반환 순서에 의존한다.
  클라이언트가 "이용약관 → 개인정보 처리방침" 같은 노출 순서를 기대하면 언젠가 어긋난다.
- **재현 조건**: 정책명 여러 건을 넣고 반복 호출/DB 재시작 후 호출.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/repository/PolicyNameRepository.java:10`
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:26`
- **심각도**: low
- **제안**: 노출 순서가 UI 계약이면 `display_order` 컬럼을 두거나, 최소한 `findAllByIsDeprecatedFalseOrderByIdAsc()`로 고정한다.

### 4. `effectiveAt`이 NULL인 정책은 조용히 "버전 없음"으로 내려간다

- **증상**: 서비스가 `effectiveAt == null`을 "미시행"으로 취급해 필터링한다.
  그런데 DDL에서 `effective_at`은 nullable이라, 운영자가 시행일을 빠뜨리고 정책을 등록하면
  에러도 로그도 없이 `version/url/isRequired`가 전부 `null`인 항목이 응답된다.
  클라이언트 입장에서는 "약관 URL이 없는 약관"을 받게 된다.
- **재현 조건**: `policies`에 `effective_at IS NULL`인 행만 있는 정책명을 만들고 API 호출.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:31`
  - `/Users/seongmin/twinly/backend/src/main/resources/db/migration/V1__init_schema.sql:363`
- **심각도**: low
- **제안**: 의도된 동작이면 그대로 두되 API 스펙에 "시행 중인 버전이 없으면 version/url/isRequired가 null"임을 명시한다.
  의도치 않은 상태라면 `effective_at`을 `NOT NULL`로 좁혀 등록 시점에 막는 편이 낫다.

### 5. 모든 정책 버전을 `content`(TEXT)까지 통째로 읽어와 애플리케이션에서 최신을 고른다

- **증상**: `findAllByPolicyNameIdIn`이 해당 정책명의 **모든 버전 행**을 엔티티로 로딩하는데,
  `Policy.content`가 `TEXT`라서 실제로 응답에 쓰지도 않는 약관 본문 전체가 매 요청마다 DB→앱으로 전송된다.
  버전이 쌓일수록 선형으로 커진다.
- **재현 조건**: 정책명당 버전을 수십 개 쌓고 `show-sql`로 조회 결과 크기를 확인.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:30`
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/entity/Policy.java:26`
- **심각도**: low
- **제안**: `content`를 뺀 프로젝션(interface/DTO projection)으로 조회하거나,
  "정책명별 시행된 최신 버전 1건"을 뽑는 쿼리로 좁힌다. (트래픽이 늘기 전까지는 급하지 않음)

### 6. 조회 전용인데 `@Transactional(readOnly = true)`가 없다

- **증상**: `policies()`가 두 번의 조회를 하는데 트랜잭션 경계가 없어 각 리포지토리 호출이 별도 트랜잭션으로 실행된다.
  두 조회 사이에 정책이 바뀌면 정책명과 버전이 어긋난 스냅샷을 볼 수 있다(발생 확률은 매우 낮음).
- **재현 조건**: 두 조회 사이에 `policy_names` 행이 삭제/추가되는 동시성 상황.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:25`
- **심각도**: low
- **제안**: `@Transactional(readOnly = true)`를 붙인다. 일관성뿐 아니라 flush 모드 최적화 이점도 있다.
