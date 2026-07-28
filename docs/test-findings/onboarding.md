# onboarding 도메인 테스트 발견사항

작성 기준: `OnboardingController` 담당 엔드포인트 11개에 대한 단위/슬라이스/통합 테스트 작성 중 발견.
운영 코드는 수정하지 않았고, 아래는 기록/제안만 한다.

---

# [이번 라운드] springdoc 스펙 기준 통합 커버리지 확장

springdoc(`/v3/api-docs`)으로 오퍼레이션을 열거해 통합 테스트를 11/11 전부로 확장했다.
그 과정에서 **실행으로 확정된** 운영 코드 버그 2건이 나왔다. (아래 BUG-ONB-01, BUG-PHOTO-01)

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`OnboardingControllerUnitTest` 23 + `OnboardingServiceUnitTest` 23 + `AiChatServiceUnitTest` 4) | **50/50 통과** |
| 통합 (`OnboardingIntegrationTest`) | **15/17 통과, 2 실패** |

실패 2건은 아래 두 버그를 그대로 드러낸 것이다. 초록불을 만들려고 테스트를 비틀지 않았고,
해당 테스트에는 `[의도된 실패 - BUG-xxx]` 주석을 달아 두었다.

| 실패 테스트 | 원인 |
|---|---|
| `설문 답변: 실제 설문 로더의 문항에 답하면 survey_answers 행이 생성된다` | BUG-ONB-01 |
| `프로필 사진 commit: 업로드가 끝난 key면 anon_session_photos 행이 생성된다` | BUG-PHOTO-01 |

## BUG-ONB-01 — `POST /api/v1/onboarding/survey-answers`가 **항상 500**이다

- **심각도**: **critical** (설문 답변 저장이 전면 불가 → 온보딩 진행 자체가 막힌다)
- **증상**: 어떤 요청이든 500 INTERNAL_ERROR. 컨텍스트 기동이나 애플리케이션 시작 시점에는 아무 문제가 없고,
  **해당 리포지토리 메서드를 처음 호출하는 순간** 쿼리 파생에 실패한다.

  ```
  BadJpqlGrammarException: UnknownPathException:
    Could not resolve attribute 'QId' of com.nidus.twinly.onboarding.entity.SurveyAnswer
    [SELECT s FROM SurveyAnswer s WHERE s.anonSessionId = :anonSessionId AND s.QId = :qId]
  ```

- **원인**: 필드명이 `qId`다. 두 번째 글자가 대문자라 JavaBeans 규약(`Introspector.decapitalize`)상
  프로퍼티명이 `qId`가 아니라 **`QId`**로 결정된다. Spring Data는 메서드명 `...AndQId`를 이 프로퍼티 `QId`로
  해석하는 데 성공하지만, Hibernate 메타모델은 **필드 접근**이라 속성명이 `qId`다. 둘이 어긋나 경로 해석이 깨진다.
- **재현 조건**: 익명 세션 인증 후 `POST /api/v1/onboarding/survey-answers` 1회 호출. 데이터 상태와 무관하게 100% 재현.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/onboarding/entity/SurveyAnswer.java:25` — `private Integer qId;`
  - `backend/src/main/java/com/nidus/twinly/onboarding/repository/SurveyAnswerRepository.java:13` — `findByAnonSessionIdAndQId`
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:94` — 호출 지점
- **왜 단위 테스트로는 안 잡혔나**: 서비스 단위 테스트는 리포지토리를 목으로 대체하므로 쿼리 파생이 아예 일어나지 않는다.
  실제 EntityManager가 붙는 통합 테스트에서만 드러난다.
- **제안(택1)**: 어느 쪽이든 한 줄 수정이다.
  ```java
  // (a) 쿼리를 명시해 파생 규칙을 우회한다 — 필드명은 그대로 둔다
  @Query("SELECT s FROM SurveyAnswer s WHERE s.anonSessionId = :anonSessionId AND s.qId = :qId")
  Optional<SurveyAnswer> findByAnonSessionIdAndQId(@Param("anonSessionId") Long anonSessionId,
                                                   @Param("qId") Integer qId);
  ```
  ```java
  // (b) 애매한 이름 자체를 없앤다 — 필드를 questionId로 바꾸고 컬럼만 유지
  @Column(name = "q_id")
  private Integer questionId;   // → findByAnonSessionIdAndQuestionId
  ```
  (b)가 근본적이다. `qId`처럼 "소문자 1글자 + 대문자" 형태의 필드는 이 함정을 계속 만든다.
  같은 패턴이 `SurveyAnswer.getQId()`를 쓰는 `OnboardingService.java:110`에도 걸쳐 있으니 함께 정리해야 한다.

## BUG-PHOTO-01 — 프로필 사진 최초 commit이 **항상 500**이다

- **심각도**: **critical** (온보딩에서 프로필 사진 등록 불가)
- **증상**: `POST /api/v1/onboarding/profile/photo/commit`이 신규 사진을 저장할 때 SQL 문법 오류로 500.

  ```
  SQLSyntaxErrorException: You have an error in your SQL syntax ...
    near 'key,type,uploaded_at,width,x_pos,y_pos) values (...)'
  [insert into anon_session_photos (anon_session_id,created_at,height,key,type,uploaded_at,width,x_pos,y_pos) values (?,?,?,?,?,?,?,?,?)]
  ```

- **원인**: 컬럼명이 `key`인데 **MySQL 예약어**다. DDL은 백틱으로 감싸 두었지만
  (`V1__init_schema.sql:76`), 엔티티에는 `@Column(name = "`key`")`가 없어 Hibernate가
  INSERT 문에 **인용부호 없이** `key`를 넣는다.
- **왜 지금까지 드러나지 않았나**
  - `ddl-auto: validate`는 메타데이터만 비교하므로 통과한다.
  - **SELECT는 정상 동작한다.** Hibernate가 별칭으로 한정(`p1_0.key`)해 생성하기 때문이다.
    즉 **읽기는 되는데 쓰기만 깨지는** 형태라 조회 위주 테스트로는 절대 안 잡힌다.
- **재현 조건**: 사진이 없는 익명 세션에서 commit 1회 호출. 100% 재현.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/anon/entity/AnonSessionPhoto.java:29` — `private String key;`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:76` — `` `key` TEXT NOT NULL ``
- **동일 결함이 me 도메인에도 있다**: `user/entity/Photo.java:30` + `V1__init_schema.sql:343`.
  자세한 내용은 [me.md](me.md) 참조.
- **제안**: 두 엔티티 모두 컬럼명을 인용부호로 감싸면 된다.
  ```java
  @Column(name = "`key`", columnDefinition = "TEXT")
  private String key;
  ```
  전역으로 막고 싶다면 `spring.jpa.properties.hibernate.globally_quoted_identifiers=true`도 방법이지만,
  모든 식별자에 영향을 주므로 지금 상황에서는 두 곳만 고치는 편이 부작용이 적다.

## 테스트 쪽 수정 (운영 코드 아님)

- presign 실패 케이스의 기대 상태코드를 422 → **415**로 정정했다.
  `ErrorCode.UNSUPPORTED_IMAGE_TYPE`이 `HttpStatus.UNSUPPORTED_MEDIA_TYPE`으로 매핑되어 있어
  415가 정상 동작이다. (`ErrorCode.java:88`)

---

## POST /api/v1/onboarding/survey-answers

### 1. 설문 "마지막 문항" 판정이 문항 개수(size)로 되어 있어, 실제 마지막 문항이 아닌 문항에서 페르소나가 생성된다

- **증상**
  설문을 끝까지 마쳤을 때 답변 전체를 페르소나 요소로 변환해야 하는데, 실제로는 설문 중간(17번째 문항)에서
  변환이 실행되고, 진짜 마지막 문항을 답한 시점에는 아무 일도 일어나지 않는다. 결과적으로 페르소나가
  **부분 답변만으로** 만들어진다. 예외가 나지 않으므로 조용히 잘못된 데이터가 쌓인다.
- **재현 조건**
  `survey/survey_v1_mixed.json`의 문항 배열 순서는 `[8, 10, 6, 1, 14, 12, 20, 13, 21, 19, 15, 2, 22, 11, 5, 7, 23, 17, 18, 16, 9, 3, 4]` (총 23개)이다.
  `SurveyLoader.lastKey()`는 `questionMap.size()` = **23**을 반환한다. 즉 "마지막 문항"이 `qId == 23`으로 판정되는데,
  `qId=23`은 노출 순서상 **17번째** 문항이다. 실제 마지막으로 노출되는 문항은 `qId=4`다.
  1. 문항을 노출 순서대로 답변한다 → 17번째(`qId=23`)를 답하는 순간 `saveAllSurveyAnswer`가 실행되어 그때까지의 17건만 페르소나로 저장된다.
  2. 나머지 6문항(17, 18, 16, 9, 3, 4)을 답해도 페르소나는 추가되지 않는다.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/common/survey/SurveyLoader.java:34` (`lastKey = questionMap.size();`)
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:100` (`if (qId.equals(surveyLoader.lastKey()))`)
- **심각도**: high
- **제안**
  `lastKey`는 "개수"가 아니라 "마지막으로 삽입된 키"여야 한다. `LinkedHashMap`을 쓰고 있으므로
  `lastKey = questionMap.keySet().stream().reduce((a, b) -> b).orElse(null);` 처럼 삽입 순서상 마지막 키를 잡거나,
  아예 판정 기준을 "모든 문항에 답했는가"(`surveyAnswerRepository.countByAnonSessionId == questionMap.size()`)로 바꾸는 편이 안전하다.
  후자는 문항 id 체계(1..N 연속 여부)에 의존하지 않아 파일이 바뀌어도 깨지지 않는다.
  (현재 구조는 문항 id가 1..N 연속이 아니게 되는 순간 `lastKey`가 **존재하지 않는 id**가 되어 페르소나가 아예 생성되지 않는다.)

### 2. 마지막 문항을 다시 답하면 페르소나 요소가 중복 저장된다

- **증상**
  `saveAllSurveyAnswer`는 삭제/중복 체크 없이 `save`만 하므로, 트리거 문항을 재답변할 때마다
  같은 내용의 `anon_session_persona_elements` 행이 계속 늘어난다. 이후 AI 프롬프트에 같은 특성이 N번 들어간다.
- **재현 조건**: 트리거가 되는 문항(현재 `qId=23`)에 A로 답한 뒤 B로 다시 답변 → 페르소나 요소가 2배로 쌓인다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:106-114`
- **심각도**: medium
- **제안** 변환 전에 해당 세션의 설문 유래 페르소나 요소를 지우거나(dimension 기준 delete 후 insert),
  이미 변환이 끝난 세션이면 재실행하지 않도록 가드를 둔다.

### 3. `saveAllSurveyAnswer`의 `@Transactional`은 자기 호출이라 동작하지 않는다

- **증상**: `surveyAnswer()`가 같은 클래스의 `saveAllSurveyAnswer()`를 직접 호출하므로 프록시를 타지 않아
  `@Transactional`이 적용되지 않는다. 지금은 호출자(`surveyAnswer`)가 이미 트랜잭션이라 문제가 없지만,
  "이 메서드는 자체 트랜잭션을 갖는다"는 오해를 만든다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:105-106`
- **심각도**: low
- **제안**: 내부 전용이면 `private`로 내리고 어노테이션을 제거한다.

---

## POST /api/v1/onboarding/interests

### 4. 관심사 목록의 원소가 null이면 500이 난다

- **증상**: `OnboardingInterestsRequest.interests`에는 `@NotNull`만 있고 원소 제약이 없다.
  `{"interests": [null]}` 요청이 검증을 통과해 `explanation TEXT NOT NULL` 컬럼에 null을 insert하려다
  `DataIntegrityViolationException` → `GlobalExceptionHandler`의 catch-all에 걸려 **500**이 된다. (400이어야 할 입력 오류)
- **재현 조건**: `POST /api/v1/onboarding/interests` 바디 `{"interests": [null]}` 또는 `[""]`(빈 문자열은 저장은 되나 무의미)
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/onboarding/dto/request/OnboardingInterestsRequest.java:7`
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:119-121`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:63` (`explanation TEXT NOT NULL`)
- **심각도**: medium
- **제안**: `@NotNull List<@NotBlank String> interests` 로 원소 제약을 걸고, 필요하면 `@Size`로 개수 상한도 둔다.

### 5. 관심사를 다시 제출하면 이전 관심사가 남아 중복 누적된다

- **증상**: 기존 INTERESTS 페르소나 요소를 지우지 않고 append만 하므로, 사용자가 관심사 화면에서 뒤로 갔다가
  다시 제출하면 관심사가 두 배로 쌓인다. (온보딩 화면 재진입은 흔한 시나리오)
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:117-122`
- **심각도**: medium
- **제안**: 세션의 `INTERESTS` 차원 요소를 삭제한 뒤 저장하는 "치환" 의미로 구현한다.

---

## POST /api/v1/onboarding/profile/nickname/check, PUT /api/v1/onboarding/profile/nickname

### 6. 자기 자신이 이미 설정한 닉네임을 다시 제출하면 409가 난다 (재제출 비멱등) — **설정 API는 해결됨, 중복 확인 API는 남음**

- **증상**: 중복 검사가 "다른 세션인지"를 구분하지 않고 `anonSessionRepository.existsByNickname(nickname)`만 본다.
  이미 `nickname=twinly`로 설정한 세션이 같은 값을 다시 제출하면 자기 자신 때문에 `NICKNAME_ALREADY_USED(409)`가 난다.
  중복 확인 API도 자기 닉네임에 대해 `isAvailable=false`를 준다. 네트워크 재시도/화면 재진입에서 바로 드러난다.
- **심각도**: medium
- **조치**: `profileNickname`·`profileNicknameCheck` 모두 중복 검사를 `existsByNicknameAndIdNot(nickname, anonSessionId)`로 교체했다.
  같은 닉네임 재제출이 멱등하게 성공하고(설정 API는 `PUT`으로 전환), 자기 닉네임 중복 확인도 `isAvailable=true`가 된다.
  `AnonSessionRepository.existsByNickname`은 호출부가 사라져 제거했다.

### 7. 검사-후-저장 사이의 경쟁 조건이 500으로 새어 나간다

- **증상**: `exists` 확인과 `changeNickname` 사이에 다른 요청이 같은 닉네임을 선점하면
  `uk_anon_sessions_nickname` / `uk_users_nickname` 위반이 flush 시점에 터져 500이 된다. (409로 매핑되지 않음)
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:157-171`
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:125` (`uk_anon_sessions_nickname`)
- **심각도**: low
- **제안**: `DataIntegrityViolationException`을 잡아 `NICKNAME_ALREADY_USED`로 변환한다(유니크 제약을 최종 방어선으로 사용).

### 8. 닉네임 정책이 금지어 검사뿐이다

- **증상**: `validateAndNormalizeNickname`은 trim + 금지어 4개 확인만 한다. 길이 상한, 허용 문자,
  공백/특수문자 규칙이 없어 500자 닉네임이나 제어문자도 통과한다. (`INVALID_NICKNAME` 코드가 사실상 금지어 전용)
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:224-236`
- **심각도**: low
- **제안**: 길이(예: 2~20자)와 허용 문자 정규식을 `@Pattern`/`@Size`로 request DTO에 선언해 400으로 거르고,
  금지어처럼 도메인 지식이 필요한 것만 서비스에 남긴다.

---

## POST /api/v1/onboarding/ai-chat/start

### 9. 두 번 호출하면 유니크 제약 위반으로 500이 난다

- **증상**: `aiChatStart`는 이미 시작된 세션인지 확인하지 않고 항상 `turnIndex=0`의 AI 메시지를 insert한다.
  `uk_ai_chats_anon_session_id_sender_turn_index (anon_session_id, sender, turn_index)`에 걸려
  `DataIntegrityViolationException` → **500**. 화면 재진입/새로고침/재시도에서 바로 발생한다.
  (게다가 예외 이전에 Bedrock을 이미 호출하므로 실패 요청마다 모델 비용이 발생한다.)
- **재현 조건**: 같은 익명 세션 토큰으로 `POST /api/v1/onboarding/ai-chat/start` 2회 호출
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/aichat/service/AiChatService.java:35-51` (특히 :44 save)
  - `backend/src/main/resources/db/migration/V1__init_schema.sql:28` (유니크 제약)
- **심각도**: medium
- **제안**: `aiChatRepository.existsByAnonSessionId(anonSessionId)`(이미 존재하는 메서드)로 확인해
  이미 시작된 세션이면 저장된 0번 턴 질문을 그대로 반환한다(멱등). 최소한 Bedrock 호출 **전에** 검사해야 한다.

---

## POST /api/v1/onboarding/ai-chat/messages

### 10. 같은 turnIndex로 두 번 답하면 500이 난다

- **증상**: 사용자 답변을 저장할 때 중복 검사가 없다. 같은 `turnIndex`로 재전송하면
  `(anon_session_id, 'USER', turn_index)` 유니크 제약 위반으로 500. 네트워크 재시도에서 발생 가능하다.
  또한 `DETAIL` 페르소나 요소도 매번 append되어 중복 누적된다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/aichat/service/AiChatService.java:78-81`
- **심각도**: medium
- **제안**: 해당 턴의 USER 메시지가 이미 있으면 저장된 다음 질문을 그대로 반환(멱등)하거나,
  명시적 도메인 에러(409)로 매핑한다.

---

## POST /api/v1/onboarding/consents

### 11. 최신/시행 중인 정책 버전인지 확인하지 않는다

- **증상**: `PolicyCatalog.loadByKey`는 `effective_at` 필터 없이 모든 버전을 키로 만든다.
  따라서 클라이언트가 **과거 버전**(`version: 1`, 이미 v2가 시행 중)으로 동의를 보내도 그대로 저장된다.
  조회 API(`LegalService.policies`)는 최신 버전만 내려주므로 정상 클라이언트는 문제없지만, 서버 검증은 없다.
- **근거 코드 위치**
  - `backend/src/main/java/com/nidus/twinly/legal/service/PolicyCatalog.java:22-33`
  - `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:186-198`
- **심각도**: low
- **제안**: 동의 대상은 "현재 시행 중인 최신 버전"만 허용하고, 그 외 버전은 `POLICY_NOT_FOUND`로 거절한다.

---

## DELETE /api/v1/onboarding/consents

### 12. 존재하지 않는 정책/버전을 철회 요청해도 200을 반환한다 (등록 API와 비대칭)

- **증상**: `grantConsents`는 정책을 못 찾으면 `POLICY_NOT_FOUND(404)`를 던지는데,
  `revokeConsents`는 `filter(policy -> policy != null)`로 **조용히 무시**하고 200을 반환한다.
  클라이언트는 철회에 실패했는데 성공으로 인지한다. 요청 전부가 잘못된 경우에도 200이다.
- **재현 조건**: `DELETE /api/v1/onboarding/consents` 바디 `{"grants":[{"policyId":"없는정책","version":"1"}]}` → 200
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:209-221`
- **심각도**: medium
- **제안**: 등록과 동일하게 조회 실패 시 `POLICY_NOT_FOUND`를 던져 동작을 대칭으로 맞춘다.

### 13. DELETE에 요청 바디를 요구한다

- **증상**: `@DeleteMapping` + `@RequestBody`. 일부 HTTP 클라이언트/프록시/캐시는 DELETE 바디를 버리거나
  전달하지 않아(RFC 9110에서도 의미가 정의돼 있지 않음) 400으로 실패할 수 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/controller/OnboardingController.java:110-113`
- **심각도**: low
- **제안**: `POST /api/v1/onboarding/consents/revoke` 처럼 바디를 자연스럽게 쓰는 형태로 바꾸거나,
  철회 대상을 쿼리 파라미터로 받는다.

---

## PUT /api/v1/onboarding/basic-info

### 14. 생년월일에 미래 날짜가 들어올 수 있다

- **증상**: `birthDate`에 `@NotNull`만 있어 `"2999-01-01"`도 통과해 그대로 저장된다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/dto/request/OnboardingBasicInfoRequest.java:15`
- **심각도**: low
- **제안**: `@Past`(또는 `@PastOrPresent`) 추가. 필요하면 연령 하한도 함께 검증한다.

### 15. `INVALID_ANON_SESSION` 분기는 사실상 도달 불가하다 (참고)

- **증상**: 인증 리졸버(`CurrentAnonSessionArgumentResolver` → `AnonService.resolveByToken`)가 이미 세션을 조회·검증했으므로,
  서비스의 `findById(...).orElseThrow(INVALID_ANON_SESSION)`는 같은 트랜잭션 안에서 실질적으로 실패하지 않는다.
  버그는 아니지만 "스냅샷을 받고 다시 조회한다"는 이중 조회 구조라 의도를 문서화하거나 정리할 여지가 있다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/onboarding/service/OnboardingService.java:66-68`
- **심각도**: low
- **제안**: 현 구조 유지가 합리적이면 그대로 두되(스냅샷은 값 객체, 수정은 엔티티로), 중복 조회 비용은 인지하고 있을 것.

---

## GET /api/v1/onboarding/survey-questions, POST /api/v1/onboarding/profile/photo/presign, POST /api/v1/onboarding/profile/photo/commit

발견된 이슈 없음.
(단, presign/commit은 `PresignService`/`PhotoCommitService`가 소유자 검증·contentType 화이트리스트·업로드 완료 확인을
이미 수행하고 있어 온보딩 서비스 계층에는 추가 결함이 보이지 않았다.)
