# activity 도메인 테스트 발견사항

대상 엔드포인트 (springdoc 스펙 기준 1개)

- `GET /api/v1/activities/{date}`

작성한 테스트

- `backend/src/test/java/com/nidus/twinly/activity/controller/ActivityControllerUnitTest.java` (3건)
- `backend/src/test/java/com/nidus/twinly/activity/service/ActivityServiceUnitTest.java` (6건)
- `backend/src/test/java/com/nidus/twinly/activity/integration/ActivityIntegrationTest.java` (3건)

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 | **9/9 통과** |
| 통합 | **3/3 통과** |

> 운영 코드는 수정하지 않았습니다. 아래는 기록만 한 발견사항입니다.

---

## 발견사항

### 1. `parseLines`의 JSON 파싱 실패가 500으로만 드러난다

- **증상**: `scenes.lines`(JSON 컬럼)를 `objectMapper.readValue`로 파싱하는데, 값이 깨져 있으면
  파싱 예외가 그대로 올라가 **500 INTERNAL_ERROR**가 된다. 하루치 활동 중 한 장면의 데이터만 깨져도
  그 날짜 조회 전체가 실패한다.
- **재현 조건**: `scenes.lines`에 배열이 아닌 JSON(예: `{}`)이 저장된 상태에서 해당 날짜 조회.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/activity/service/ActivityService.java:114`
- **심각도**: low (생성 주체가 내부 파이프라인이라 외부 입력으로는 발생시키기 어려움)
- **제안**: 파싱 실패 시 해당 장면의 대사만 빈 배열로 흘려보내고 경고 로그를 남기면 하루치 조회 전체가 죽는 것을 막을 수 있다.

### 2. 탈퇴/삭제된 동석자의 이름이 `null`로 내려간다

- **증상**: `toSpeakerResult`는 `partnerUserById`에서 유저를 못 찾으면 `userName`을 `null`로 채운다.
  block 도메인은 같은 상황을 `탈퇴한 사용자`로 마스킹하는데, activity는 `null`을 내려보내
  **동일 상황에 대한 응답 표현이 도메인마다 다르다**. 클라이언트가 null 처리를 빠뜨리면 화면이 깨진다.
- **재현 조건**: 동석자 유저가 삭제된 상태에서 해당 날짜 활동 조회.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/activity/service/ActivityService.java:107`
  (비교 대상: `backend/src/main/java/com/nidus/twinly/block/service/BlockService.java:58`)
- **심각도**: low
- **제안**: 표기 규칙을 한 곳으로 모아 두 도메인이 같은 문자열을 쓰게 하는 편이 계약이 명확하다.

### 3. `version`을 첫 번째 장면에서만 가져온다

- **증상**: 응답의 `version`이 `scenes.get(0).getVersion()`으로 결정된다. 같은 날짜의 장면들이
  서로 다른 version을 가지면 **첫 장면의 값만 대표로 나가** 나머지 장면과 어긋난다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/activity/service/ActivityService.java:64`
- **심각도**: low
- **제안**: 하루 단위로 version이 하나라는 것이 불변식이라면 그 불변식을 저장 시점에서 보장하고,
  아니라면 장면별로 version을 내려야 한다. 현재는 어느 쪽인지 코드만 봐서는 알 수 없다.
