# anon 도메인 테스트 발견사항

담당 엔드포인트: `POST /api/v1/anon/start`


## 테스트 통과 여부 (springdoc 스펙 기준, 이번 라운드 최종)

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`AnonControllerUnitTest` 2 + `AnonServiceUnitTest` 5) | **7/7 통과** |
| 통합 (`AnonIntegrationTest`) | **2/2 통과** |

springdoc이 노출하는 이 도메인의 오퍼레이션 1개는 단위·통합 양쪽 모두 커버되어 있다.
**이번 라운드에서 새로 확인된 운영 코드 버그는 없다.** 아래 발견사항은 이전 라운드의 기록이며 여전히 유효하다.

---

## POST /api/v1/anon/start

기능적 버그(잘못된 응답 / 데이터 오염 / 예외 미처리)는 **발견되지 않음**.
테스트 작성 중 확인한 설계상 위험/개선 여지만 아래에 기록한다.

### 1. 리소스를 생성하는 POST인데 200 OK를 반환한다

- **증상**: 새 익명 세션 리소스를 생성하지만 `201 Created`가 아니라 `200 OK`로 응답한다.
  클라이언트 계약상 오류는 아니지만, 다른 생성 API와 상태 코드 규약이 어긋날 여지가 있다.
- **재현 조건**: `POST /api/v1/anon/start` 호출 시 항상.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/anon/controller/AnonController.java:16`
- **심각도**: low
- **제안**: 프로젝트 전역의 생성 API 상태 코드 규약을 먼저 정하고 일괄 정렬한다.
  이미 클라이언트가 200을 전제로 구현되어 있다면 바꾸지 않는 편이 낫다(호환성 trade-off).

### 2. (참고) 공통 익명 인증 경로 `AnonService.resolveByToken`의 만료 경계

- **증상**: 만료 판정이 `expiresAt.isBefore(Instant.now())`라, `expiresAt`과 정확히 같은 순간은 아직 유효로 취급된다.
  실무상 무해한 마이크로초 단위 경계 문제이며 버그로 보지 않는다.
- **재현 조건**: `expiresAt == now`인 정확한 순간(사실상 재현 불가).
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/anon/service/AnonService.java:38`
- **심각도**: low
- **제안**: 조치 불필요. 기록만 남긴다.
