# connection 도메인 WebSocket(STOMP) 테스트 발견사항

담당 범위: springwolf(AsyncAPI)에 선언된 connection 도메인 WebSocket 전체.

| 방향 | 주소 | 담당 코드 |
|------|------|-----------|
| 아웃바운드 | `/user/queue/connection/control` | `ConnectionControlNotifier.publishDraining` |

인바운드(`@MessageMapping`)는 없다. **클라이언트 명령 없이 서버가 일방적으로 미는 control 채널**이다.

운영 코드는 **수정하지 않았다.** 아래는 테스트를 만들고 실행하며 발견/의심된 사항만 기록한다.

> 표기: `근거` 는 `파일:라인`. `(미검증)` 은 실행으로 확인하지 못하고 코드 정독만으로 판단한 건이다.

---

## 테스트 통과 여부

### 단위 테스트 — 전부 통과 (`./gradlew test`)

`common/websocket/notifier/ConnectionControlNotifierUnitTest.java` (**신규**)

| 케이스 | 결과 |
|--------|------|
| 접속 중인 모든 유저의 `/queue/connection/control` 로 control 봉투 팬아웃 (`v`/`kind`/`type`/`reason`/`retryAfterMs`) | ✅ 통과 |
| `retryAfterMs = null` 을 그대로 실어 보냄 (스펙상 nullable) | ✅ 통과 |
| 접속자가 0명이면 전송 호출 자체가 없음 | ✅ 통과 |

### 통합 테스트 — 전부 통과 (`./gradlew integrationTest`)

`connection/integration/ConnectionControlWebSocketIntegrationTest.java` (**신규**, 실제 소켓 + Testcontainers MySQL)

| 케이스 | 결과 |
|--------|------|
| draining 예고 e2e (접속 → 구독 → control 프레임 수신, `reason` 이 `"deploy"` 로 직렬화) | ✅ 통과 |
| 접속자 2명 팬아웃 + 개인 큐 격리(중복 수신 없음), `retryAfterMs=null` 전달 | ✅ 통과 |

**촉발 방식에 대한 한계**: 이 채널은 인바운드 명령이 없고, 게다가 운영 코드에 `notifyDraining` 호출자가 없어서(아래 버그 1번) 통합 테스트가 **notifier 빈을 직접 호출**해 촉발한다. 그래도 실제 소켓 · 실제 `SimpUserRegistry` 등록 · `/user` 개인화 · JSON 직렬화(enum `@JsonProperty`)는 전부 실제로 관통한다. 다만 **"어떤 상황에서 이 프레임이 나가는가"는 검증하지 못한다** — 그 트리거가 아직 없기 때문이다.

---

## 발견된 운영 코드 문제 (수정하지 않음)

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
