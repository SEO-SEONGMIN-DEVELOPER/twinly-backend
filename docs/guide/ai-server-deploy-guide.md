# AI 서버 배포 가이드 (인프라)

AI 서버를 stage / prod 에 올릴 때 필요한 네트워크·계정 정보와, 새로 만들어야 하는 리소스 목록입니다.
**VPC·서브넷·NAT·라우팅은 이미 만들어져 있고, AI 서버용 자리도 미리 잘라놨습니다.** AI 팀은 그 자리에 인스턴스를 올리고 배포 파이프라인을 붙이면 됩니다. 데이터베이스는 **AI 전용 RDS 를 따로 띄웁니다** — [ai-server-infra-change-guide.md](ai-server-infra-change-guide.md) 를 보세요.

| 항목 | 값 |
| --- | --- |
| AWS 계정 | `968177690717` |
| 리전 | `ap-northeast-2` (서울) |
| 사용 AZ | `ap-northeast-2a`, `ap-northeast-2c` |
| 아키텍처 | 기존 서버 전부 **arm64** (`t4g` 계열) |

---

## 1. 이미 만들어져 있는 것

### 1-1. VPC

| 환경 | VPC | CIDR |
| --- | --- | --- |
| stage | `vpc-020560e6f60670a83` (`twinly-stage-vpc`) | `10.0.0.0/16` |
| prod | `vpc-053461950e25144e2` (`twinly-prod-vpc`) | `10.1.0.0/16` |

stage 와 prod 는 **완전히 분리된 VPC** 입니다. 피어링이 없으므로 **사설 경로로는** stage AI 서버가 prod 백엔드에 닿을 수 없고, 그 반대도 안 됩니다.

다만 이 격리를 만능으로 믿으면 안 됩니다. 3절의 호출 경로는 퍼블릭 ALB 도메인이라 인터넷을 한 번 거치므로, **stage AI 서버에 prod URL 을 넣으면 그대로 호출됩니다.** VPC 분리는 사설망 사고만 막습니다. 그러니 베이스 URL 은 코드나 이미지에 박지 말고 **환경별 `ai/env` 파라미터로만 주입**하세요. 어느 환경을 보는지가 인스턴스가 읽는 파라미터 하나로 결정되면, 코드에 잘못된 주소가 섞여 들어갈 경로가 없어집니다.

### 1-2. 서브넷

주소는 계층마다 16의 배수에서 시작합니다. 그래야 CIDR 경계가 떨어져 `10.x.16.0/22` 처럼 한 줄로 묶입니다. 각 계층은 AZ-a / AZ-c 한 쌍입니다.

| 환경 | 이름 | Subnet ID | CIDR | AZ | 용도 |
| --- | --- | --- | --- | --- | --- |
| stage | `twinly-stage-public-a` | `subnet-0ddcbb98f8d965d85` | `10.0.0.0/24` | a | ALB, NAT |
| stage | `twinly-stage-public-c` | `subnet-02f0aaffb5b6d41e0` | `10.0.1.0/24` | c | ALB |
| stage | `twinly-stage-private-api-a` | `subnet-0f2a6135f6cddb5bb` | `10.0.16.0/22` | a | 백엔드 API |
| stage | `twinly-stage-private-api-c` | `subnet-038fb6c4397467ecc` | `10.0.20.0/22` | c | 백엔드 API |
| stage | `twinly-stage-private-monitoring-a` | `subnet-0aa5629cd8450bf3f` | `10.0.24.0/24` | a | 모니터링 |
| stage | `twinly-stage-private-monitoring-c` | `subnet-048392e5bb9bfb99a` | `10.0.25.0/24` | c | 모니터링 |
| stage | **`twinly-stage-private-ai-a`** | **`subnet-0a28172df026d0501`** | `10.0.32.0/22` | a | **AI 서버** |
| stage | **`twinly-stage-private-ai-c`** | **`subnet-070efd393c092035d`** | `10.0.36.0/22` | c | **AI 서버** |
| stage | `twinly-stage-private-db-a` | `subnet-09ccd24867a5b9fdd` | `10.0.48.0/24` | a | RDS, Redis |
| stage | `twinly-stage-private-db-c` | `subnet-02bbe7e2fff28a28a` | `10.0.49.0/24` | c | RDS, Redis |
| prod | `twinly-prod-public-a` | `subnet-0daf48f859aeb820d` | `10.1.0.0/24` | a | ALB, NAT |
| prod | `twinly-prod-public-c` | `subnet-0b15ff61b2a544f17` | `10.1.1.0/24` | c | ALB, NAT |
| prod | `twinly-prod-private-api-a` | `subnet-03667d6159a37c189` | `10.1.16.0/22` | a | 백엔드 API |
| prod | `twinly-prod-private-api-c` | `subnet-03f8b1e6457d26d86` | `10.1.20.0/22` | c | 백엔드 API |
| prod | `twinly-prod-private-monitoring-a` | `subnet-065cbb5ffc4308a60` | `10.1.24.0/24` | a | 모니터링 |
| prod | `twinly-prod-private-monitoring-c` | `subnet-03dbd2043e6cdb321` | `10.1.25.0/24` | c | 모니터링 |
| prod | **`twinly-prod-private-ai-a`** | **`subnet-0fb7f4824922d3624`** | `10.1.32.0/22` | a | **AI 서버** |
| prod | **`twinly-prod-private-ai-c`** | **`subnet-04888affed9ad4ee9`** | `10.1.36.0/22` | c | **AI 서버** |
| prod | `twinly-prod-private-db-a` | `subnet-071ccf72f1d0866d3` | `10.1.48.0/24` | a | RDS |
| prod | `twinly-prod-private-db-c` | `subnet-0d6f811d5e00e8bf0` | `10.1.49.0/24` | c | RDS |

`private-ai` 서브넷은 **AI 서버 전용으로 비워둔 자리**입니다. `/22` 라 AZ 당 약 1,000개의 사설 IP가 있으니 인스턴스 수를 늘려도 IP가 모자랄 일은 없습니다.

**AI 서버는 반드시 `private-ai` 서브넷에 올리세요.** 서브넷 위치가 곧 권한 경계입니다. 보안그룹과 ALB 호출 출처 제한이 이 서브넷 배치를 기준으로 동작합니다([ai-server-infra-change-guide.md](ai-server-infra-change-guide.md) 참고).

### 1-3. 라우팅 / 인터넷 연결

| 환경 | AI 서브넷이 붙은 라우팅 테이블 | 0.0.0.0/0 경로 |
| --- | --- | --- |
| stage | `twinly-stage-private-rt` (`rtb-0b64ccb6dc5a22b15`) | `nat-055b8bae24b270aff` (NAT, AZ-a) |
| prod (a) | `twinly-prod-private-rt-a` (`rtb-09ae176fa175c88ea`) | `nat-00e5e08675542025f` (NAT, AZ-a) |
| prod (c) | `twinly-prod-private-rt-c` (`rtb-0065fd129eba2d95f`) | `nat-069a878f42714dcec` (NAT, AZ-c) |

- **AI 서버는 퍼블릭 IP를 갖지 않습니다.** 인바운드는 인터넷에서 절대 닿지 않고, 아웃바운드(Bedrock, PyPI, ECR 등)는 NAT 를 통해 나갑니다. 서브넷 생성 시 이미 자동 퍼블릭 IP 할당을 꺼둔 상태이므로 인스턴스 생성 옵션에서도 켜지 마세요.
- NAT 아웃바운드 EIP: stage `43.200.93.8` / prod `52.78.136.250`(a), `43.200.73.21`(c). 외부 API 화이트리스트 등록이 필요하면 이 IP를 주면 됩니다.
- prod 에는 S3 게이트웨이 엔드포인트(`vpce-0528a95d59ae1cc78`)가 붙어 있어 S3·ECR 레이어 다운로드가 NAT 를 타지 않습니다. **stage 에는 없으므로** stage 에서 이미지를 자주 받으면 NAT 데이터 처리 요금이 붙습니다(GB 당 약 $0.045). 이미지 크기를 줄이는 게 곧 비용 절감입니다.
- stage NAT 는 AZ-a 한 대뿐입니다. AZ-c 의 AI 서버 아웃바운드도 AZ-a NAT 를 지나므로 cross-AZ 전송 요금이 붙고, NAT 가 죽으면 양쪽 AZ 가 같이 나갑니다. stage 라 감수한 선택이고, prod 는 AZ 별 NAT 로 분리돼 있습니다.

### 1-4. 백엔드 쪽 기존 리소스

| 환경 | 항목 | 값 |
| --- | --- | --- |
| stage | API 도메인 | `https://stage-api.trytwinly.com` |
| stage | ALB | `twinly-stage-alb` / SG `sg-06e0f2662d4362983` |
| stage | API 인스턴스 | `i-0373d39748d04d49b`(a), `i-09ddde9020d7a94ca`(c) / SG `sg-0c26384f3e1a5f3ab` |
| stage | RDS | `twinly-stage-api-rds` / SG `sg-07f04cde29b45da5f` (`twinly-stage-api-rds-sg`) |
| stage | 모니터링 | API-a 인스턴스에 함께 기동 (전용 인스턴스 없음) |
| prod | API 도메인 | `https://api.trytwinly.com` |
| prod | ALB | `twinly-prod-alb` / SG `sg-00999185cfed7cce7` |
| prod | API 인스턴스 | `i-08903483652243fec`(a), `i-027f1e44635da471e`(c) / SG `sg-0e53de8cd50b33929` |
| prod | RDS | `twinly-prod-api-rds` / SG `sg-09362c60a1248fe03` (`twinly-prod-api-rds-sg`) |
| prod | 모니터링 | `i-0f9ae4a42137c72f9` / SG `sg-037b89a9f0043787f` |

API 서버는 8080(서비스) / 8081(actuator) 을 열고, **인바운드는 ALB 와 모니터링 SG 에서만** 허용합니다.

---

## 2. AI 서버 배포를 위해 필요한 것

VPC 레벨은 손댈 게 없습니다. 아래 8개가 전부이고, 그중 하나는 이미 끝나 있습니다.

| # | 리소스 | stage 이름 | prod 이름 | 상태 | 상세 |
| --- | --- | --- | --- | --- | --- |
| 1 | 보안그룹 | `twinly-stage-ai-sg` | `twinly-prod-ai-sg` | 해야 함 | 2-1 |
| 2 | EC2 인스턴스 역할 | `twinly-stage-ai-ec2-role` | `twinly-prod-ai-ec2-role` | 해야 함 | 2-2 |
| 3 | ECR 리포지토리 | `twinly-ai` (환경 공용, 태그로 구분) | 동일 | 해야 함 | 2-3 |
| 4 | SSM 파라미터(환경변수) | `/twinly/stage/ai/env` | `/twinly/prod/ai/env` | 해야 함 | 2-4 |
| 5 | CloudWatch 로그 그룹 | `/twinly/stage/ai` | `/twinly/prod/ai` | 해야 함 | 2-5 |
| 6 | AI 서버 EC2 | `twinly-stage-ai-a` / `-c` | `twinly-prod-ai-a` / `-c` | 해야 함 | 6절 |
| 7 | AI 전용 RDS + SG | `twinly-stage-ai-rds-sg` 등 | 동일 | 해야 함 | [ai-server-infra-change-guide.md](ai-server-infra-change-guide.md) 3절 |

만드는 순서는 **1 → 2 → 6 → 3·4·5 → 7** 입니다. 보안그룹과 IAM 역할은 EC2 를 만들 때 필요하고, 나머지는 EC2 가 뜬 뒤 배포 단계에서 쓰입니다. 7번(AI RDS 의 SG)은 1번이 있어야 걸 수 있습니다.

### 2-1. 보안그룹

AI 서버는 **누구의 요청도 받지 않고, 자기가 배치로 호출만 하는 구조**입니다.

| 방향 | 규칙 | 이유 |
| --- | --- | --- |
| 인바운드 | 메트릭 포트 ← `twinly-{env}-monitoring-sg` | Prometheus 가 긁어가는 경로. 그 외에는 없음 |
| 아웃바운드 | 전체 허용 (`0.0.0.0/0`) | Bedrock·백엔드 API·RDS·ECR·패키지 저장소 호출 |

SSH 는 열지 않습니다. 접속은 SSM Session Manager 로 합니다(5절).

```bash
aws ec2 create-security-group --profile infra --region ap-northeast-2 \
  --group-name twinly-stage-ai-sg \
  --description "ai server, metrics in from monitoring only" \
  --vpc-id vpc-020560e6f60670a83 \
  --tag-specifications 'ResourceType=security-group,Tags=[{Key=Name,Value=twinly-stage-ai-sg}]'
```

**보안그룹 설명(`--description`)은 생성 후 변경할 수 없습니다.** 처음 만들 때 제대로 적어두세요.

모니터링은 백엔드와 같은 Prometheus·Grafana 를 씁니다. AI 서버를 새로 세울 필요 없이 [monitoring/prometheus/{env}.yml](../../monitoring/prometheus) 에 job 을 추가하면 됩니다. Prometheus 는 pull 방식이라 대상이 늘어도 서버는 한 대면 충분합니다. 대신 대상 IP를 설정 파일에 직접 쓰므로, **AI 인스턴스를 교체하면 이 파일도 고쳐서 모니터링을 재배포**해야 합니다.

### 2-2. EC2 인스턴스 역할

백엔드 API 역할(`twinly-stage-api-ec2-role`)과 같은 구성에서 필요 없는 것만 뺀 형태입니다.

| 정책 | 용도 | 비고 |
| --- | --- | --- |
| `AmazonSSMManagedInstanceCore` | SSM 접속·배포 명령 수신 | **필수** (없으면 배포도 접속도 불가) |
| `AmazonEC2ContainerRegistryReadOnly` | ECR 이미지 pull | 필수 |
| `CloudWatchAgentServerPolicy` | 로그·메트릭 전송 | 필수 |
| `TwinlyStageAiReadEnv` / `TwinlyProdAiReadEnv` | `/twinly/{env}/ai/*` 파라미터 읽기 | **신규 생성** (아래 참고) |
| `TwinlyBedrockInvokePolicy` | `bedrock:InvokeModel` | AI 서버가 Bedrock 을 쓸 경우 |

파라미터 읽기 정책은 **기존 `TwinlyStageReadEnv` 를 재사용하지 마세요.** 그 정책은 `parameter/twinly/stage/*` 전체를 허용하므로, 붙이는 순간 AI 서버가 백엔드의 `/twinly/stage/api/env`(DB 비밀번호·JWT 서명키·외부 서비스 키가 전부 들어 있음)까지 읽을 수 있게 됩니다. AI 서버가 필요한 건 자기 것뿐이니 경로를 좁힌 정책을 새로 만드세요. 파라미터를 `/twinly/{env}/ai/` 아래에 모아둔 이유가 이것입니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:GetParameter",
      "Resource": "arn:aws:ssm:ap-northeast-2:968177690717:parameter/twinly/stage/ai/*"
    },
    {
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "*",
      "Condition": {
        "StringEquals": { "kms:ViaService": "ssm.ap-northeast-2.amazonaws.com" }
      }
    }
  ]
}
```

**Bedrock 은 액세스 키가 아니라 인스턴스 역할로 호출하세요.** 백엔드는 과거 사정으로 IAM 사용자 키를 환경변수에 넣고 있지만, EC2 에서 도는 새 서비스는 역할을 붙이면 키를 발급·보관·교체할 필요가 없습니다. 유출 사고의 뿌리를 없애는 쪽이 맞습니다.

### 2-3. ECR

백엔드와 리포지토리를 공유하지 말고 `twinly-ai` 를 따로 파세요. 태그 정책·수명주기 정책·푸시 권한을 서로 독립적으로 가져가야 한쪽 실수가 다른 쪽 배포를 막지 않습니다.

- 리포지토리: `968177690717.dkr.ecr.ap-northeast-2.amazonaws.com/twinly-ai`
- 이미지 태그: 백엔드와 동일하게 **커밋 SHA** 를 씁니다. `latest` 는 어떤 커밋이 떠 있는지 추적이 안 되므로 쓰지 마세요.
- **이미지는 arm64 로 빌드**해야 합니다. 기존 인스턴스가 전부 `t4g`(Graviton) 이고 AI 서버도 같은 계열로 갈 예정이면 `docker build --platform linux/arm64` 가 필요합니다. x86 인스턴스를 쓰기로 했다면 그건 그것대로 괜찮지만, **인스턴스 타입과 이미지 아키텍처는 반드시 맞추세요.** 이 불일치가 배포 실패 원인 1순위입니다.

### 2-4. 환경변수 (SSM 파라미터)

백엔드와 같은 방식입니다. `.env` 파일 전체를 SecureString 파라미터 하나에 통째로 넣고, 배포 시 인스턴스가 내려받아 `.env` 로 씁니다. 파일명은 인스턴스에서 `.env` 그대로입니다.

| 항목 | 값 |
| --- | --- |
| 이름 | `/twinly/stage/ai/env`, `/twinly/prod/ai/env` |
| 타입 | `SecureString` |
| 티어 | 4096자를 넘으면 `Advanced` (백엔드 블롭이 그렇습니다) |
| 값 | `KEY=VALUE` 줄바꿈 형식 |

최소한 아래 키는 들어가야 합니다.

```
BASE_URL=https://stage-api.trytwinly.com
DB_HOST=<AI 전용 RDS 엔드포인트 — ai-server-infra-change-guide.md 3절 참고>
DB_PORT=<엔진 포트, PostgreSQL 이면 5432>
DB_NAME=...
DB_USERNAME=...
DB_PASSWORD=...
IMAGE_TAG=...
AWS_REGION=ap-northeast-2
```

**비밀값의 원본은 이 블롭 하나뿐입니다.** 같은 값을 별도 파라미터에 사본으로 두지 않습니다. 두 곳에 두면 반드시 어긋납니다. 슬랙·PR·리포지토리에 붙여넣지도 마세요.

파라미터 값을 갱신할 때는 **끝의 개행에 주의하세요.** `aws ssm get-parameter --output text` 는 출력에 개행을 덧붙이므로, 받아서 고쳐 다시 넣기를 반복하면 값 끝에 빈 줄이 계속 쌓입니다.

### 2-5. 로그

`docker compose` 의 `awslogs` 드라이버로 CloudWatch 에 직접 보냅니다(백엔드와 동일). 로그 그룹 `/twinly/{env}/ai`, 스트림은 인스턴스 ID 로 두면 어느 인스턴스가 뱉은 로그인지 바로 구분됩니다.

### 2-6. 데이터베이스

> **이 절은 폐기되었습니다.** 백엔드 RDS 공유안이 AI 전용 RDS 분리로 확정되면서
> 여기 있던 내용 전체(`twinly_ai` 계정·데이터베이스, 백엔드 RDS 보안그룹 규칙 추가,
> 마스터 비밀번호 절차)가 무효입니다. 백엔드 RDS 에는 접속하지 않으며, SG 에 규칙을
> 추가하지도 않습니다.
>
> 현행 지침은 [ai-server-infra-change-guide.md](ai-server-infra-change-guide.md) 를 보세요.
> 3절에 AI 전용 RDS 의 권장 사양·네트워크·접속 정보 관리가 정리되어 있습니다.

---

## 3. 백엔드 호출 경로

AI 서버가 부르는 백엔드 API 계약은 [docs/api-spec/internal-api-for-ai.md](../api-spec/internal-api-for-ai_v1.md) 를 보세요. 여기서는 **네트워크 경로만** 다룹니다.

현재 백엔드는 AI 서버를 호출하지 않습니다. 트래픽은 **AI → 백엔드 단방향**뿐이므로 백엔드 쪽 보안그룹에 열어줄 것도 없습니다.

인프라 관점에서 하나 짚어두면, **AI 가 넘기는 시각 값은 전부 시뮬레이션 하루 안의 상대 시각이고 서버 시계에서 유도하지 않습니다.** 그래서 EC2 가 UTC 로 뜨는지 KST 로 뜨는지는 저장되는 데이터에 영향을 주지 않습니다. 인스턴스 타임존은 기본값(UTC) 그대로 두면 됩니다.

AI 서버는 백엔드의 퍼블릭 도메인을 그대로 호출합니다.

```
AI 서버 → NAT → 인터넷 → ALB → API 인스턴스
```

이 경로를 위해 새로 만들 네트워크 리소스는 없습니다. ALB 가 API 인스턴스 두 대로 분산하므로 한 대가 죽어도 배치는 계속 돌고, 호출량이 하루 배치 수준이라 왕복 지연과 NAT 데이터 요금은 무시할 수준입니다.

베이스 URL 은 `ai/env` 파라미터(2-4)로 주입하세요. 코드나 이미지에 박으면 환경을 바꿀 때마다 이미지를 다시 만들어야 합니다.

| 환경 | 베이스 URL |
| --- | --- |
| stage | `https://stage-api.trytwinly.com` |
| prod | `https://api.trytwinly.com` |

---

## 4. 배포 파이프라인

백엔드 [.github/workflows/cd.yml](../../.github/workflows/cd.yml) 과 동일한 구조를 그대로 따라가면 됩니다. 새로 발명할 것이 없습니다.

```
GitHub Actions (OIDC 로 역할 assume)
  → docker build & ECR push (태그 = 커밋 SHA)
  → aws ssm send-command 로 인스턴스에 배포 명령 전달
       - SSM 파라미터에서 .env 내려받기
       - IMAGE_TAG 치환
       - ECR 로그인 → docker compose pull → up -d
       - 헬스체크가 통과할 때까지 대기
```

핵심은 **SSH 를 쓰지 않는다**는 점입니다. AI 서버는 사설 서브넷에 있고 22번 포트도 안 열려 있으므로, 배포는 전부 SSM 을 경유합니다. 배스천을 새로 세울 필요가 없습니다.

준비해야 할 것:

| 항목 | 내용 |
| --- | --- |
| GitHub Actions 역할 | AI 리포지토리용 OIDC 역할 신설 (`repo:{org}/{ai-repo}:ref:refs/heads/main` 로 trust 제한) |
| 권한 | ECR push(`twinly-ai` 한정) + `ssm:SendCommand`(**AI 인스턴스 ID 한정**) + `ssm:GetCommandInvocation` |
| 배포 단위 | stage 는 main 머지 시 자동, prod 는 태그 푸시 시 수동 트리거 (백엔드와 동일) |

권한 리소스를 와일드카드로 열지 마세요. 기존 `TwinlySSMDeploy` 도 인스턴스 ID 를 하나씩 박아뒀습니다. AI 파이프라인이 실수로 API 서버에 명령을 보내는 상황을 IAM 단에서 막기 위해서입니다.

---

## 5. 인스턴스 접속

키페어가 없으므로 SSH 로는 못 들어갑니다. SSM 으로 접속하세요.

```bash
aws ssm start-session --profile infra --region ap-northeast-2 --target {instance-id}
```

접속이 안 되면 대개 셋 중 하나입니다.

1. 인스턴스 역할에 `AmazonSSMManagedInstanceCore` 가 없음
2. NAT 경로가 막혀서 SSM 에이전트가 엔드포인트에 도달 못 함
3. 에이전트 미설치 (Ubuntu 공식 AMI 는 기본 설치되어 있음)

---

## 6. AI 서버 EC2 띄우기

### 6-1. 설정값

| 항목 | 값 |
| --- | --- |
| AMI | Ubuntu 24.04 arm64 (백엔드와 동일: `ami-05cd5f557a0769fae`) |
| 인스턴스 타입 | arm64 계열 (`t4g.*`). x86 을 쓰려면 AMI 와 이미지 빌드 아키텍처를 함께 바꿔야 함 |
| 서브넷 | `twinly-{env}-private-ai-a` / `-c` |
| 퍼블릭 IP | **비활성** |
| 보안그룹 | `twinly-{env}-ai-sg` |
| IAM 인스턴스 프로파일 | `twinly-{env}-ai-ec2-role` |
| 키페어 | 없음 (SSM 으로 접속) |
| 루트 볼륨 | gp3 30GB (백엔드와 동일. 이미지 여러 버전이 쌓이므로 8GB 기본값은 금방 참) |
| IMDS | `http-tokens=required`, `hop-limit=2` (IMDSv2 강제, 컨테이너에서 메타데이터 조회 가능) |
| user-data | [aws/user-data/api.sh](../../aws/user-data/api.sh) 를 AI 서버용으로 복사해 사용 |
| Name 태그 | `twinly-{env}-ai-a` / `twinly-{env}-ai-c` |

### 6-2. 순서

1. **2-1 보안그룹**, **2-2 IAM 역할**을 먼저 만듭니다. 인스턴스 생성 후에 붙일 수도 있지만, 역할 없이 뜬 인스턴스는 SSM 에 등록되지 않아 접속조차 안 됩니다.
2. user-data 스크립트를 준비합니다. [aws/user-data/api.sh](../../aws/user-data/api.sh) 가 Docker + AWS CLI(aarch64) + CloudWatch Agent + `mysql-client` 설치 후 `/home/ubuntu/twinly` 작업 디렉터리를 만드는 내용이니, 경로만 AI 서버용으로 바꿔 쓰면 됩니다. **user-data 는 최초 부팅 때 한 번만 실행됩니다.** 이후 변경은 인스턴스를 새로 띄워야 반영됩니다.
3. `run-instances` 로 AZ-a, AZ-c 각각 생성합니다.
4. `aws ssm start-session` 으로 접속되는지 확인합니다.
5. 배포 역할의 `ssm:SendCommand` 리소스 목록에 **새로 만든 인스턴스 ID 를 추가**합니다. 이 단계를 빼먹으면 파이프라인이 `AccessDenied` 로 떨어집니다.
6. RDS 보안그룹에 인바운드를 추가하고(2-6), `twinly_ai` 비밀번호를 설정합니다.

```bash
aws ec2 run-instances --profile infra --region ap-northeast-2 \
  --image-id ami-05cd5f557a0769fae \
  --instance-type t4g.medium \
  --subnet-id subnet-0a28172df026d0501 \
  --security-group-ids {twinly-stage-ai-sg 의 ID} \
  --iam-instance-profile Name=twinly-stage-ai-ec2-role \
  --no-associate-public-ip-address \
  --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}' \
  --metadata-options 'HttpTokens=required,HttpPutResponseHopLimit=2' \
  --user-data file://aws/user-data/ai.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=twinly-stage-ai-a}]'
```

AZ-c 는 `--subnet-id subnet-070efd393c092035d`, Name 태그 `twinly-stage-ai-c` 로 같은 명령을 한 번 더 실행합니다. prod 서브넷 ID 는 1-2 표를 보세요.

인스턴스 타입·대수는 AI 워크로드에 맞춰 AI 팀이 정하시면 됩니다. 다만 **AZ 두 곳에 나눠 배치**하는 편이 좋습니다. 서브넷은 이미 a/c 양쪽에 준비돼 있고, 배치 작업이라도 AZ 하나가 통째로 나갔을 때 하루치가 통으로 밀리는 건 피하는 게 낫습니다.

### 6-3. 뜬 다음 확인할 것

| 확인 | 방법 | 실패하면 |
| --- | --- | --- |
| SSM 등록 | `aws ssm describe-instance-information` 목록에 인스턴스 ID 가 보이는지 | IAM 역할 또는 NAT 경로 문제 |
| 아웃바운드 | 인스턴스 안에서 `curl -I https://api.trytwinly.com` | 라우팅/SG 확인 |
| ECR pull | ECR 로그인 후 `docker pull` | 역할에 `AmazonEC2ContainerRegistryReadOnly` 누락 |
| 파라미터 조회 | `aws ssm get-parameter --name /twinly/{env}/ai/env --with-decryption` | `Twinly{Env}AiReadEnv` 정책 누락 |
| DB 접속 | AI 전용 RDS 에 접속 ([ai-server-infra-change-guide.md](ai-server-infra-change-guide.md) 3절) | `twinly-{env}-ai-rds-sg` 인바운드 또는 계정 설정 확인 |

---

## 7. 운영 들어가기 전 챙길 것

### 7-1. 배치 중복 실행

6절에서 AZ 두 곳에 인스턴스를 나누라고 했는데, **배치를 인스턴스 안 cron 으로 돌리면 두 대가 같은 날짜를 각각 처리합니다.** 백엔드 `POST /internal/v1/users/{userId}/simulations` 는 같은 `(userId, date)` 로 다시 호출하면 이전 데이터를 지우고 버전을 올리므로 데이터가 깨지지는 않지만, LLM 호출 비용이 그대로 두 배가 됩니다.

셋 중 하나로 정하고 시작하세요.

| 방식 | 내용 | 적합한 경우 |
| --- | --- | --- |
| 단일 실행 인스턴스 | 배치는 AZ-a 한 대에서만 돌리고 나머지는 대기 | 지금 규모에 가장 단순 |
| 유저 분할 | userId 를 인스턴스 수로 나눠 각자 자기 몫만 처리 | 하루치가 한 대로 안 끝날 때 |
| 외부 트리거 | EventBridge → SSM 으로 한 대만 지정 실행 | 스케줄을 코드 밖에서 관리하고 싶을 때 |

### 7-2. 알람

백엔드는 CloudWatch 알람 정의를 [aws/cloudwatch/alarms](../../aws/cloudwatch/alarms) 에 사본으로 두고 있습니다. 배치는 사람이 안 보고 있을 때 도는 작업이라, 알람이 없으면 며칠 뒤에야 실패를 압니다.

| 대상 | 지표 | 상태 |
| --- | --- | --- |
| RDS | `FreeStorageSpace`, `DatabaseConnections`, `CPUUtilization`, `FreeableMemory` | **AI 전용 RDS 용으로 신규 생성.** 백엔드 것(`twinly-{env}-api-rds-*`)을 참고해 같은 형식으로 |
| EC2 | `StatusCheckFailed` | AI 인스턴스용으로 신규 생성 |
| 배치 자체 | 실패 시 로그 metric filter → 알람 | 신규 생성. 인스턴스는 멀쩡한데 배치만 실패하는 경우를 잡음 |

### 7-3. 리소스 정의 사본

이 리포지토리는 콘솔에서 만든 IAM·CloudWatch 리소스의 정의를 [aws/](../../aws) 아래에 JSON 사본으로 남기는 규칙을 씁니다. AI 서버용으로 만든 역할·정책·알람도 같은 형식으로 커밋해주세요. 콘솔만 보고 "이 권한이 왜 붙어 있지"를 나중에 되짚는 일이 없어집니다.

### 7-4. 롤백

이미지 태그가 커밋 SHA 이므로, 롤백은 **직전 SHA 로 배포 파이프라인을 다시 도는 것**입니다. 별도 절차가 없습니다. 다만 DB 스키마를 바꾼 배포는 이미지만 되돌린다고 복구되지 않으니, 마이그레이션은 이전 버전 코드와도 동작하도록(컬럼 추가는 nullable 로, 삭제는 다음 배포에서) 나눠 적용하세요.

---

## 8. 자주 나오는 질문

**Q. AI 서버에서 백엔드 데이터(`twinly_api`)를 직접 읽으면 안 되나요?**
안 됩니다. `twinly_ai` 계정은 `twinly_ai` 데이터베이스에만 권한이 있어 `twinly_api` 테이블은 조회조차 되지 않습니다(권한 오류). 같은 RDS 인스턴스를 쓰지만 데이터는 분리돼 있습니다. 필요한 데이터는 전부 `/internal/v1/**` API 로 받으세요. 백엔드 스키마 변경이 곧바로 AI 장애로 번지는 결합을 만들지 않기 위한 경계입니다.

**Q. AI 서버가 stage 백엔드와 prod 백엔드를 같이 보게 할 수 있나요?**
하지 마세요. 환경마다 AI 서버를 따로 띄웁니다. 3절 경로(퍼블릭 도메인 호출)에서는 URL 만 바꾸면 기술적으로 닿기 때문에, "VPC 가 분리돼 있으니 안전하다"고 믿으면 안 됩니다(1-1 참고). 베이스 URL 은 반드시 환경별 `ai/env` 파라미터로만 주입하세요.

**Q. DB 접속이 `Access denied` 로 거부됩니다.**
셋 중 하나입니다. ① AI 서버가 `private-ai` 서브넷 밖에 있음 — 계정이 `10.x.32.0/21` 대역에서만 로그인을 허용합니다. ② RDS 보안그룹에 인바운드를 아직 안 걸었음(2-6). ③ 비밀번호가 `ai/env` 값과 DB 실제 값이 다름.

**Q. 외부 API(OpenAI 등)를 호출해야 하는데 되나요?**
NAT 를 통해 나갑니다. 상대 쪽에 IP 화이트리스트가 필요하면 1-3 의 NAT EIP 를 등록하세요.

**Q. 서브넷 IP 가 모자라지 않나요?**
`/22` = AZ 당 약 1,000개입니다. 컨테이너를 몇 개를 띄우든 인스턴스 ENI 단위로만 소비되므로 여유가 큽니다.
