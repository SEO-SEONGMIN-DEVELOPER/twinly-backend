# AI 서버 → 백엔드 호출 경로 변경

2026-09-03 stage·prod 적용. AI 서버가 백엔드 internal API 를 부르는 경로가 인터넷 경유에서 VPC
내부 경유로 바뀌었습니다.

| | 이전 | 이후 |
| --- | --- | --- |
| 경로 | AI → NAT → 인터넷 → 퍼블릭 ALB → API | AI → internal ALB `:8080` → API |
| 프로토콜 | HTTPS | HTTP (VPC 밖으로 나가지 않음) |

| 환경 | 이전 베이스 URL | 이후 베이스 URL |
| --- | --- | --- |
| stage | `https://stage-api.trytwinly.com` | `http://internal-twinly-stage-internal-alb-1008314660.ap-northeast-2.elb.amazonaws.com:8080` |
| prod | `https://api.trytwinly.com` | `http://internal-twinly-prod-internal-alb-714551485.ap-northeast-2.elb.amazonaws.com:8080` |

AI 팀이 할 일은 `ai/env` 의 베이스 URL 을 위 값으로 바꾸는 것뿐입니다. API 경로, 요청/응답
계약은 그대로입니다.

두 환경 모두 퍼블릭 ALB 의 `/internal/*` 은 출처와 무관하게 403 입니다. 이전 주소로 호출하면
실패합니다.

prod 는 `twinly-prod-ai-sg` 와 AI 인스턴스가 아직 없어, AI 인스턴스에서 internal ALB 로 들어가는
보안그룹 규칙이 걸려 있지 않습니다. AI 팀이 SG 와 인스턴스를 만들면 ID 를 백엔드에 전달해주세요.

확인은 AI 인스턴스에서 아래를 실행해 `403`(권한 없음, 즉 API 앱까지 도달)이 나오면 됩니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://internal-twinly-stage-internal-alb-1008314660.ap-northeast-2.elb.amazonaws.com:8080/internal/v1/users/1/persona
```
