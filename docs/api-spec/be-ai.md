# Backend ↔ AI 엔드포인트

---

## 1. Backend → AI 풀시뮬 서비스 (`{ai.base-url}`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/full-sim` | internal-token | `{ matchId, personaA, personaB, seed, options:{progressEventsTarget}, callbackUrl, callbackToken }` | `202 { jobId, matchId, etaSec }` | 풀시뮬 비동기 잡 시작(결과는 webhook). **백엔드가 실제 호출하는 유일 엔드포인트** |
| GET | `/full-sim/{jobId}` | internal-token | — | `{ jobId, matchId, status, progressPct, etaSec, partial }` | 잡 상태 폴링. **백엔드 미사용**(webhook 모드) |
| DELETE | `/full-sim/{jobId}` | internal-token | — | `202 { jobId }` | 잡 취소. **백엔드 미사용** |
| POST | `/mini-sim` | internal-token | `{ pairId, personaA, personaB, maxTurns }` | `MiniSimResponse` | 미니시뮬. **백엔드 제거됨**(AiClient 메서드 없음), AI 서버에만 잔존 |
| GET | `/healthz` | public | — | `{ status:"ok" }` | liveness |
| GET | `/readyz` | public | — | `{ status:"ready" }` (LLM 키 미설정 시 503) | readiness |

> `personaA`/`personaB` = `{ userId, nickname, age, gender, traits:object }`. `school` 미전송. `seed=matchId`(멱등), `progressEventsTarget=72`.
> 에러: `409 SIM_JOB_DUP`(멱등), `429 SIM_OVERLOAD`(backpressure→재큐), 기타 4xx(fast-fail), 5xx/timeout(재시도).
> FastAPI 앱 2개 모두 `/full-sim` 노출: `ai/src/api/main.py`(운영), `ai/full_sim/api.py`(vNext). 클라이언트 모드 `twinly.ai.client`: `stub`(기본)/`http`/`claude-stub`.

---

## 2. AI → Backend webhook (`{ai.callback-base-url}`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/internal/full-sim/callback` | callback-token | `{ jobId, matchId, status, completedAt, result:{…}, metrics }` | `204` | 시뮬 완료 결과 수신(`MatchWebhookController`) |

> `callback-token` = `sha256Hex("{webhookSecret}:{matchId}")`. 불일치 → `401 FULL_SIM_TOKEN_INVALID`.
> `result` = `{ relation('ROMANCE'→'LOVER'), affinityScore, novel(string\|{A,B}), dialogues:[{simDay,speaker,text}], innerThoughts:[{simDay,userId,text}], progressEvents:[{simDay,title,summary,mood,pushTitle}] }`. `novelTier`/`hesitation` 은 무시.
> `status`=`failed`/`canceled` → `error.{code,message}` 읽고 sim FAILED 후 204. 에러: `400 CALLBACK_BODY_INVALID`, `404 MATCH_NOT_FOUND`/`SIMULATION_NOT_FOUND`.

---

## 3. Backend → 외부 LLM Gemini (`https://generativelanguage.googleapis.com`)

| Method | Path | Auth | Request | Response | 용도 |
|--------|------|------|---------|----------|------|
| POST | `/v1beta/models/{model}:generateContent` | api-key | `{ systemInstruction, contents:[…], generationConfig:{responseMimeType:"application/json"} }` | `{ candidates:[…] }` | `continueChat`/`extractPersona`/`generateChatPrompts` 공용 |

> `LlmClient` 메서드 3개 — `continueChat`(온보딩 채팅, model=`chat-model`), `extractPersona`(페르소나 추출, `extract-model`), `generateChatPrompts`(대화거리, `prompts-model`). model default 전부 `gemini-2.5-flash-lite`.
> `twinly.llm.gemini-api-key` 비면 `StubLlmClient` fallback. 매칭/망설임용 LLM 메서드 없음.
