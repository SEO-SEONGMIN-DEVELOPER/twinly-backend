# API 에러 처리 가이드 (프론트엔드용)

이 문서는 **에러 응답 규약**과 **에러 코드 카탈로그**를 담습니다.
각 엔드포인트가 실제로 어떤 코드를 반환하는지는 **OpenAPI 스펙(Swagger UI / Apidog)의 각 API `Responses`** 에 명시되어 있으며, 이 문서는 그 코드들의 **의미 사전** 역할을 합니다.

> 원천(SSOT): 백엔드 `com.nidus.twinly.common.web.ErrorCode` enum. enum이 바뀌면 이 문서도 갱신해야 합니다.

---

## 1. 에러 응답 규약

모든 HTTP 에러 응답은 아래 형식으로 내려갑니다.

```json
{
  "code": "POLICY_NOT_FOUND",
  "message": "존재하지 않는 정책 또는 버전입니다."
}
```

| 필드 | 용도 |
|---|---|
| `code` | **분기용 안정 식별자.** 프론트는 이 값으로 분기합니다. 함부로 바뀌지 않는 계약입니다. |
| `message` | **사람이 읽는 문구.** 그대로 노출하거나, `code`를 키로 자국어 메시지에 매핑해 쓰세요. 문구는 바뀔 수 있으니 분기 기준으로 쓰지 마세요. |

**처리 원칙**
1. `code`로 분기한다. `message`로 분기하지 않는다.
2. HTTP status는 큰 분류(4xx/5xx)로만 참고하고, 정확한 분기는 `code`로 한다.
3. **방어 규칙**: `code`가 없으면(`null`) HTTP status로 폴백한다. (프레임워크 내부가 던지는 예외는 아직 `code`가 없을 수 있음)

```ts
// 예시
try {
  await api.post("/api/v1/me/consents", body);
} catch (e) {
  const { code, message } = e.response.data; // { code, message }
  switch (code) {
    case "POLICY_NOT_FOUND": /* 정책 목록 새로고침 */ break;
    case "UNAUTHORIZED":
    case "INVALID_TOKEN":
    case "TOKEN_EXPIRED":     /* 재로그인 유도 */ break;
    case "WITHDRAWN_USER":    /* 탈퇴 안내 화면 */ break;
    default:                  /* message 노출 또는 status 폴백 */ break;
  }
}
```

---

## 2. 공통 에러 (모든 엔드포인트 / 전역 규약)

아래는 개별 엔드포인트 문서에 반복 표기하지 않습니다. **모든 엔드포인트에 공통**입니다.

| HTTP | code | 언제 | 적용 범위 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | 요청 바디/파라미터 검증 실패, path 변수 형식 오류 등 | 전체 |
| 401 | 인증 방식별 코드 | 인증 실패 | **인증이 필요한 엔드포인트만** |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류 | 전체 |
| 404 | `NOT_FOUND` | **존재하지 않는 경로**로 요청 | 라우팅 단계 (특정 엔드포인트의 응답이 아님) |
| 405 | `METHOD_NOT_ALLOWED` | 존재하는 경로에 **지원하지 않는 HTTP 메서드**로 요청 | 라우팅 단계 (특정 엔드포인트의 응답이 아님) |

> **401 참고**: OpenAPI의 각 오퍼레이션에는 해당 인증 방식에서 실제로 발생할 수 있는 코드가 모두 표기됩니다.
> - `@CurrentUser`(액세스 토큰): `UNAUTHORIZED`, `INVALID_TOKEN`, `WITHDRAWN_USER`
> - `@CurrentAnonSession`(익명 세션): `UNAUTHORIZED`, `INVALID_TOKEN`, `INVALID_ANON_SESSION`, `TOKEN_EXPIRED`
>
> 대부분 동일하게 "재인증"으로 처리하되, `WITHDRAWN_USER`(탈퇴)만 별도 UX가 필요할 수 있습니다.

> **404/405 참고**: 이 둘은 **라우팅 단계**에서 발생하므로 개별 오퍼레이션 문서에는 표기하지 않습니다.
> 문서에 존재하는 경로·메서드로 요청하면 발생할 수 없기 때문입니다. 오퍼레이션의 404는
> `USER_NOT_FOUND`처럼 그 엔드포인트가 정의한 도메인 코드입니다.

---

## 3. 에러 코드 카탈로그 (전체 62개)

### 공통
| code | HTTP | 의미 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 형식이 올바르지 않습니다. |
| `UNAUTHORIZED` | 401 | 인증이 필요합니다. |
| `FORBIDDEN` | 403 | 권한이 없습니다. |
| `NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 요청 방식입니다. |
| `INTERNAL_ERROR` | 500 | 일시적인 서버 오류가 발생했습니다. |

### 인증 / 토큰 (401)
| code | HTTP | 의미 |
|---|---|---|
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰입니다. |
| `TOKEN_EXPIRED` | 401 | 만료된 토큰입니다. |
| `WITHDRAWN_USER` | 401 | 탈퇴한 유저입니다. |
| `INVALID_ANON_SESSION` | 401 | 유효하지 않은 익명 세션입니다. |
| `INVALID_REFRESH_TOKEN` | 401 | 유효하지 않은 리프레시 토큰입니다. |
| `REFRESH_TOKEN_ALREADY_REVOKED` | 401 | 이미 무효화된 리프레시 토큰입니다. |

### 인증 (회원가입 / 인증번호)
| code | HTTP | 의미 |
|---|---|---|
| `EMAIL_NOT_REGISTERED` | 404 | 가입되지 않은 이메일입니다. |
| `PHONE_NOT_REGISTERED` | 404 | 가입되지 않은 전화번호입니다. |
| `EMAIL_ALREADY_REGISTERED` | 409 | 이미 가입된 이메일입니다. |
| `PHONE_ALREADY_REGISTERED` | 409 | 이미 가입된 전화번호입니다. |
| `VERIFICATION_NOT_FOUND` | 404 | 유효하지 않은 인증 요청입니다. |
| `SIGNUP_SESSION_NOT_FOUND` | 404 | 회원가입에 사용할 세션을 찾을 수 없습니다. |
| `VERIFICATION_CODE_EXPIRED` | 410 | 인증번호가 만료되었습니다. |
| `VERIFICATION_EXPIRED` | 410 | 인증이 만료되었습니다. |
| `VERIFICATION_CODE_MISMATCH` | 422 | 인증번호가 일치하지 않습니다. |
| `EMAIL_DOMAIN_NOT_SUPPORTED` | 422 | 가입할 수 없는 이메일 도메인입니다. |
| `SMS_VERIFICATION_NOT_COMPLETED` | 422 | SMS 인증이 완료되지 않았습니다. |
| `EMAIL_VERIFICATION_NOT_COMPLETED` | 422 | 이메일 인증이 완료되지 않았습니다. |
| `PROFILE_NOT_COMPLETED` | 422 | 가입에 필요한 프로필 정보가 완성되지 않았습니다. |

### 유저 / 회원
| code | HTTP | 의미 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | 존재하지 않는 유저입니다. |
| `WITHDRAWAL_RECOVERY_EXPIRED` | 422 | 복구 가능 기간이 지났습니다. |

### 온보딩 / 닉네임 / 설문
| code | HTTP | 의미 |
|---|---|---|
| `NICKNAME_ALREADY_USED` | 409 | 이미 사용 중인 닉네임입니다. |
| `INVALID_NICKNAME` | 422 | 사용할 수 없는 닉네임입니다. |
| `SURVEY_QUESTION_NOT_FOUND` | 404 | 존재하지 않는 질문입니다. |

### 동의 (정책)
| code | HTTP | 의미 |
|---|---|---|
| `POLICY_NOT_FOUND` | 404 | 존재하지 않는 정책 또는 버전입니다. |
| `REQUIRED_POLICY_REVOKE_DENIED` | 403 | 필수 정책은 철회할 수 없습니다. |

### 알림
| code | HTTP | 의미 |
|---|---|---|
| `APP_NOTIFICATION_NOT_FOUND` | 404 | 존재하지 않는 알림입니다. |

### 망설임 (hesitation)
| code | HTTP | 의미 |
|---|---|---|
| `HESITATION_NOT_FOUND` | 404 | 존재하지 않는 망설임입니다. |
| `NOT_HESITATION_OWNER` | 403 | 본인의 망설임이 아닙니다. |
| `HESITATION_ALREADY_HANDLED` | 409 | 이미 처리된 망설임입니다. |
| `HESITATION_ANSWER_EMPTY` | 422 | 답변 내용이 없습니다. |
| `HESITATION_ANSWER_NOT_IN_OPTIONS` | 422 | 선택지에 없는 답변입니다. |

### 채팅
| code | HTTP | 의미 |
|---|---|---|
| `ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방입니다. |
| `MATCH_NOT_FOUND` | 404 | 존재하지 않는 매칭입니다. |
| `CHAT_PARTICIPATION_NOT_FOUND` | 404 | 참여 정보가 없습니다. |
| `NOT_MATCH_PARTICIPANT` | 403 | 이 매칭의 참여자가 아닙니다. |
| `NOT_ACTIVE_ROOM_PARTICIPANT` | 403 | 더 이상 참여 중이지 않은 채팅방입니다. |
| `ROOM_CLOSED` | 409 | 종료된 채팅방입니다. |
| `MESSAGE_LENGTH_EXCEEDED` | 422 | 메시지 길이 상한을 초과했습니다. |
| `MESSAGE_NOT_IN_ROOM` | 422 | 해당 방에 존재하지 않는 메시지입니다. |
| `CLIENT_MSG_ID_CONFLICT` | 409 | 이미 다른 내용으로 사용된 clientMsgId입니다. |

### 관계 / 사람
| code | HTTP | 의미 |
|---|---|---|
| `ENCOUNTER_NOT_FOUND` | 404 | 만난 적 없는 상대입니다. |
| `RELATIONSHIP_NOT_FOUND` | 404 | 관계 없는 상대입니다. |
| `INVALID_DATE_RANGE` | 400 | 조회 기간이 올바르지 않습니다. |

### AI 채팅
| code | HTTP | 의미 |
|---|---|---|
| `AI_QUESTION_NOT_FOUND` | 404 | 해당 턴의 AI 질문이 존재하지 않습니다. |

### 신고
| code | HTTP | 의미 |
|---|---|---|
| `CANNOT_REPORT_SELF` | 422 | 자기 자신을 신고할 수 없습니다. |
| `SCENE_NOT_FOUND` | 404 | 존재하지 않는 씬입니다. |
| `NOT_SCENE_OWNER` | 403 | 본인의 씬이 아닙니다. |
| `SCENE_TARGET_MISMATCH` | 422 | 대상과 씬이 일치하지 않습니다. |

### 차단
| code | HTTP | 의미 |
|---|---|---|
| `CANNOT_BLOCK_SELF` | 422 | 자기 자신을 차단할 수 없습니다. |

### 시즌
| code | HTTP | 의미 |
|---|---|---|
| `SEASON_NOT_JOINABLE` | 422 | 지금은 시즌 참가 기간이 아닙니다. |

### 사진 / 업로드
| code | HTTP | 의미 |
|---|---|---|
| `NOT_KEY_OWNER` | 403 | 본인 소유의 key가 아닙니다. |
| `UPLOAD_NOT_COMPLETED` | 422 | 업로드가 완료되지 않은 key입니다. |
| `UNSUPPORTED_IMAGE_TYPE` | 415 | 지원하지 않는 이미지 형식입니다. |

### 외부 연동
| code | HTTP | 의미 |
|---|---|---|
| `EMAIL_SEND_FAILED` | 502 | 이메일 발송에 실패했습니다. |
| `SMS_SEND_FAILED` | 502 | SMS 발송에 실패했습니다. |

---

## 4. 참고: WebSocket(STOMP) 채팅 에러는 별도 형식

실시간 채팅(STOMP) 커맨드의 에러는 위 REST `{ code, message }` 형식이 **아니라**, 커맨드 응답 페이로드의 `CommandError`(별도 `CommandErrorCode`)로 전달됩니다. REST 에러 코드와 혼동하지 마세요. (상세는 채팅 WebSocket 스펙 참조)
