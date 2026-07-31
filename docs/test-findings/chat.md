# chat 도메인 테스트 발견사항

담당 범위: `ChatController` (REST 8개 엔드포인트). WebSocket 컨트롤러는 이번 범위 제외.
운영 코드는 수정하지 않았고, 아래는 테스트 작성 중 코드를 읽으며 발견/의심된 사항만 기록한다.

> 표기: `근거` 는 `파일:라인`. `(미검증)` 표시가 있는 항목은 실행으로 확인하지 못하고 코드 정독만으로 판단한 건이다.

---

# [이번 라운드] springdoc 스펙 기준 통합 커버리지 확장

통합 테스트를 3건 → 13건으로 늘려 chat REST 8개 오퍼레이션을 전부 관통시켰다.
**새로 확인된 운영 코드 버그는 없다.**

## 테스트 통과 여부

| 구분 | 결과 |
|---|---|
| 단위/슬라이스 (`ChatControllerUnitTest` 13 + `ChatServiceUnitTest` 17 + 그 외 17) | **47/47 통과** |
| 통합 (`ChatIntegrationTest`) | **13/13 통과** |

## 추가한 통합 테스트

| 오퍼레이션 | 검증 내용 |
|---|---|
| `GET /chat/rooms/{roomId}` | 입장 동의 상태가 각자의 DB 상태대로 응답 / 매칭 당사자 아니면 403 `NOT_MATCH_PARTICIPANT` / 없는 방이면 404 `ROOM_NOT_FOUND` |
| `POST /chat/rooms/{roomId}/hide` | `isHidden=true` 저장 + **목록 조회에서 실제로 제외**되는지까지 확인 |
| `POST /chat/rooms/{roomId}/leave` | `leftAt` 기록 |
| `GET /chat/rooms/{roomId}/messages` | 커서 쿼리가 id 내림차순으로 동작 / 발신자 구분 ME·THEM / `limit` 초과 시 `hasMore=true` + `nextCursor` |
| `POST /chat/rooms/{roomId}/read` | 읽음 포인터 전진 + **목록의 안읽음 수가 0으로 집계**되는지까지 확인 / 방에 없는 메시지 id면 422 `MESSAGE_NOT_IN_ROOM` |

## 확인된 직렬화 계약 (테스트 기대값 정정 사유)

통합 테스트 작성 중 기대값을 두 번 정정했다. **둘 다 운영 코드가 옳고 테스트가 틀린 경우**였으며,
문서화 가치가 있어 남긴다.

- `senderType`은 대문자 enum명이 아니라 **소문자**로 직렬화된다 (`me` / `them` / `system`).
  `ChatSenderType`의 각 상수에 `@JsonProperty("me")` 등이 붙어 있다.
- 채팅방 상세의 상대 정보 필드명은 `partner.nickname`이 아니라 **`partner.userName`**이다.
  값은 `User.getNickname()`에서 채워지므로 필드명과 출처가 어긋나 있다.
  (`ChatRoomDetailPartnerResponse.java` / `ChatService.java:263`)
  동작 결함은 아니지만, 이름만 보고는 실명이 내려간다고 오해하기 쉽다. **심각도: info**

---

## 작성한 테스트에서 의도적으로 제외한 케이스

아래 케이스는 **위 버그 때문에 현재 실패하거나 500이 나므로** 테스트로 고정하지 않았다. 운영 코드 수정 후 추가할 것.

| 케이스 | 관련 항목 |
| --- | --- |
| enter/hide/leave — 참여 정보가 없을 때의 상태 코드 | 2 |
| `POST /messages` — 닫힌 방/나간 방으로의 전송 차단 | 3 |

메시지가 없는 방·매칭 0건 유저의 목록과 `limit` 범위 밖 요청은 해당 버그가 수정되면서 테스트로 고정됐다
(`ChatIntegrationTest`, `ChatControllerUnitTest`).

---

**남은 항목 없음.** 기록돼 있던 발견사항은 모두 처리되었거나 판단으로 닫혔다. 이력은 [_summary.md](_summary.md) 참조.
