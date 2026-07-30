# legal 도메인 테스트 발견사항


## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`LegalControllerUnitTest` 3 + `LegalServiceUnitTest` 4 + `PolicyCatalogUnitTest` 4) | **11/11 통과** |
| 통합 (`LegalIntegrationTest`) | **2/2 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
정책 버전·발효 처리(B3)는 처리 완료했고, 아래는 아직 남은 항목이다.

---

## GET /api/v1/legal/policies

### 1. 응답 배열의 순서가 보장되지 않는다

- **증상**: `findAllByIsDeprecatedFalse()`에 정렬 기준이 없어 응답 `policies` 배열의 순서가 DB 반환 순서에 의존한다.
  클라이언트가 "이용약관 → 개인정보 처리방침" 같은 노출 순서를 기대하면 언젠가 어긋난다.
- **재현 조건**: 정책명 여러 건을 넣고 반복 호출/DB 재시작 후 호출.
- **근거 코드 위치**:
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/repository/PolicyNameRepository.java:10`
  - `/Users/seongmin/twinly/backend/src/main/java/com/nidus/twinly/legal/service/LegalService.java:26`
- **심각도**: low
- **제안**: 노출 순서가 UI 계약이면 `display_order` 컬럼을 두거나, 최소한 `findAllByIsDeprecatedFalseOrderByIdAsc()`로 고정한다.

### 2. 모든 정책 버전을 `content`(TEXT)까지 통째로 읽어와 애플리케이션에서 최신을 고른다

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
