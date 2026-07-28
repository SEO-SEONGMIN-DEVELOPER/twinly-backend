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

### 1. `notifyDraining` 을 호출하는 곳이 없어 control 채널이 실제로는 한 번도 발화되지 않는다
- **증상**: `ConnectionControlNotifier.notifyDraining` 의 호출자가 운영 전체에 **0건**이다. AsyncAPI 문서에는 `/user/queue/connection/control` 채널과 "서버가 연결 종료를 예고함" 설명이 노출되지만, 실제로 예고를 보내는 코드 경로가 없다.
- **영향**: 배포·점검 시 클라이언트는 **예고 없이 소켓이 끊긴다.** 클라이언트가 `retryAfterMs` 기반 백오프를 구현해 놓아도 쓰이지 않으므로, 재배포 순간 전체 클라이언트가 동시에 즉시 재연결을 시도한다(thundering herd).
- **재현 조건**: 서버를 정상 종료(graceful shutdown)해 본다 → control 프레임 없이 연결만 끊긴다.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/common/websocket/notifier/ConnectionControlNotifier.java:27-33` (`notifyDraining` — 호출자 없음)
  - `backend/src/main/java/com/nidus/twinly/common/websocket/notifier/ConnectionControlNotifier.java:35-42` (아웃바운드 선언)
- **심각도**: **medium** (스펙 대비 기능 누락. 장애는 아니지만 무중단 배포의 전제가 성립하지 않는다)
- **제안**: 종료 훅에서 호출한다. 가장 작은 변경은 `ContextClosedEvent` 리스너를 하나 두는 것이다. 다만 예고 후 실제 종료까지 유예 시간이 필요하므로, graceful shutdown 타임아웃과 `retryAfterMs` 를 함께 정해야 실효가 있다.
  ```java
  @EventListener(ContextClosedEvent.class)
  public void onShutdown() {
      connectionControlNotifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L);
  }
  ```
  `MAINTENANCE` / `OVERLOAD` / `REALTIME_DISABLED` 는 각각 별도의 트리거(운영 API, 부하 감지, 기능 플래그)가 필요하다. **지금 당장 필요한 트리거 하나만** 붙이고 나머지는 필요할 때 추가하는 것을 권한다.

### 2. 인메모리 SimpleBroker + 로컬 `SimpUserRegistry` 라 서버가 2대 이상이면 팬아웃이 절반만 나간다 (미검증)
- **증상**: `notifyDraining` 은 `simpUserRegistry.getUsers()` 로 대상자를 정하는데, 이 레지스트리는 **해당 JVM에 붙은 세션만** 알고 있다. 브로커도 외부 릴레이 없이 `enableSimpleBroker("/queue")` 인메모리 브로커다. 인스턴스 2대 운영 시 A에서 예고를 발화하면 B에 붙은 클라이언트는 받지 못한다.
- **재현 조건**: 인스턴스 2대 뒤에 LB를 두고 각 인스턴스에 클라이언트를 하나씩 붙인 뒤, 한쪽에서 `notifyDraining` 호출.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/common/websocket/config/WebSocketConfig.java:82-84` (`enableSimpleBroker`, 릴레이 없음)
  - `backend/src/main/java/com/nidus/twinly/common/websocket/notifier/ConnectionControlNotifier.java:32` (`simpUserRegistry.getUsers()`)
- **심각도**: **medium** (단일 인스턴스 운영 중이라면 지금은 무해. 스케일아웃 시점에 반드시 터진다)
- **제안**: 지금 단일 인스턴스라면 **당장 고칠 필요는 없다.** 다만 스케일아웃 전에 (a) 브로커 릴레이(RabbitMQ STOMP 등) 도입 + `UserRegistryBroadcast` 설정, 또는 (b) 각 인스턴스가 자기 세션에만 예고를 보내도록 종료 훅을 인스턴스 로컬로 설계, 둘 중 하나를 정해야 한다. 종료 예고는 원래 "이 인스턴스가 내려간다"는 뜻이므로 (b)가 의미상 더 맞다.

### 3. 팬아웃이 호출 스레드에서 동기 루프로 돈다 (미검증)
- **증상**: `simpUserRegistry.getUsers().forEach(...)` 로 접속자 수만큼 `convertAndSendToUser` 를 **호출 스레드에서 순차 실행**한다. 접속자가 수만 명이면 호출 스레드가 그동안 잡히고, 종료 훅에서 호출할 경우 graceful shutdown 이 늘어진다.
- **근거 코드 위치**: `backend/src/main/java/com/nidus/twinly/common/websocket/notifier/ConnectionControlNotifier.java:32`
- **심각도**: **low** (현재 규모에서는 문제되지 않는다)
- **제안**: 지금은 그대로 둔다. 접속자 규모가 커지면 브로드캐스트 destination(`convertAndSend("/topic/...")`)으로 바꾸거나 별도 스레드로 빼는 것을 검토한다. 다만 개인화가 필요 없는 control 메시지라면 애초에 `/topic` 이 더 맞는 구조다 — **개인 큐 팬아웃을 택한 이유가 있는지 한 번 점검할 가치는 있다.**

### 4. 클래스는 `common.websocket`, payload/enum은 `connection` 패키지에 있어 소속이 갈린다
- **증상**: `ConnectionControlNotifier` 는 `common.websocket.notifier` 에 있는데 그것이 다루는 `ConnectionDrainingPayload` 와 `ConnectionDrainingReason` 은 `connection` 도메인 패키지에 있다. 다른 도메인(chat)은 notifier도 도메인 패키지 안에 있다.
- **근거 코드 위치**:
  - `backend/src/main/java/com/nidus/twinly/common/websocket/notifier/ConnectionControlNotifier.java:1`
  - `backend/src/main/java/com/nidus/twinly/connection/dto/websocket/ConnectionDrainingPayload.java:1`
- **심각도**: **low** (동작에 영향 없음. 다만 "이 코드 어디 있지"를 매번 헷갈리게 한다)
- **제안**: `connection.notifier` 로 옮겨 `ChatNotifier` 와 배치 규칙을 맞추거나, 반대로 "WS 인프라 성격이라 common에 둔다"를 규칙으로 명시한다. 어느 쪽이든 **규칙이 하나면 된다.** 테스트도 운영 패키지를 미러링하므로 지금은 테스트가 `common/websocket/notifier` 에 있다.
