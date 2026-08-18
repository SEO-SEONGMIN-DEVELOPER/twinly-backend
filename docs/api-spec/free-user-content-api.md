# 무료 사용자 컨텐츠 API 스펙 (앱용)

결제(시뮬레이션 참가) 전 사용자가 체험하는 두 컨텐츠의 계약 문서입니다.

| 파트 | 컨텐츠 | 요약 |
|---|---|---|
| A | 평행우주 관계 | 실제 친구와의 페르소나 유사도를 관계 5단계와 이야기로 |
| B | 하루 관람 | 무작위로 배정된 유료 사용자의 하루치 시뮬레이션 관람 |

---

# A. 평행우주 관계

공유형 컨텐츠입니다. 두 유저의 페르소나 유사도를 계산해
평행우주에서의 관계 5단계와 이야기를 돌려줍니다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/api/v1/parallel-relation-codes` | 내 코드 발급(멱등) |
| POST | `/api/v1/parallel-relations` | 친구 코드 제출 → 결과 생성 |
| GET | `/api/v1/parallel-relations` | 내 결과 목록 |
| GET | `/api/v1/parallel-relations/{parallelRelationId}` | 결과 단건 |

기본 흐름은 다음과 같습니다.

```
A: POST /api/v1/parallel-relation-codes   → 코드 발급, 카카오톡 등으로 공유
B: (앱 설치 → 온보딩 → 회원가입)
B: POST /api/v1/parallel-relations        → 코드 제출, 결과 즉시 확인
A: GET  /api/v1/parallel-relations        → 목록에서 결과 확인
```

---

## 0. 공통 규칙

### 인증

네 엔드포인트 모두 **JWT 액세스 토큰이 필요합니다.**

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

익명 세션 토큰(온보딩용)으로는 호출할 수 없습니다. 유사도 계산이 회원가입 시
이관되는 `persona_elements`(userId 기준)를 읽기 때문에, 온보딩 중에는 계산 자체가
불가능합니다. **앱에서 코드 입력 화면은 회원가입 완료 이후에 배치해야 합니다.**

### 숫자 ID는 전부 문자열

`parallelRelationId`, `userId` 등 모든 숫자 ID는 **요청·응답 모두 문자열**입니다.

```json
{ "parallelRelationId": "1041" }
```

### 시각 포맷

모든 시각은 **KST 오프셋(`+09:00`)이 붙은 ISO-8601 문자열**입니다. 서버가 `Instant`
필드를 KST로 직렬화하도록 설정되어 있어, 응답에 UTC(`Z`) 표기는 나오지 않습니다.

```json
{ "createdAt": "2026-08-18T12:11:22+09:00" }
```

### 에러

**이 문서는 성공 응답만 다룹니다.** 엔드포인트별로 나갈 수 있는 에러 코드와 상태
코드는 백엔드 구현 시점에 정리해 별도로 전달합니다.

---

## 1. POST /api/v1/parallel-relation-codes

내 코드를 발급합니다. **멱등입니다.** 이미 코드가 있으면 같은 코드를 그대로 돌려줍니다.

### Request Body

없습니다.

### Response 200

```json
{
  "code": "K7M2QX",
  "shareMessage": "트윈리에서 나랑 평행우주에서 무슨 사이인지 확인해봐! 코드: K7M2QX"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 6자리 대문자·숫자. 혼동 문자(`0`, `O`, `1`, `I`) 제외 |
| `shareMessage` | string | 공유용 문구. 앱은 그대로 공유 시트에 넣으면 됩니다 |

### 특성

- **만료가 없습니다.** 남이 주워 쓰더라도 "나와의 유사도 결과 한 건"이 생길 뿐이라
  차단할 실익이 없습니다.
- **한 코드를 여러 친구가 사용할 수 있습니다.** 단톡방 공유를 전제로 합니다.
- 호출할 때마다 새 코드를 만들지 않습니다. 이미 뿌린 코드가 죽으면 유저는 그 사실을
  알 수 없기 때문입니다.

---

## 2. POST /api/v1/parallel-relations

친구에게 받은 코드를 제출해 결과를 생성합니다.

### Request Body

```json
{ "code": "K7M2QX" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `code` | string | O | 대소문자는 서버에서 대문자로 정규화합니다 |

### Response 201

`GET /api/v1/parallel-relations/{parallelRelationId}`와 **동일한 스키마**입니다.
제출 직후 결과 화면을 바로 그릴 수 있도록 전체를 내려줍니다.

### Response 200

**이미 그 친구와의 결과가 있으면 새로 만들지 않고 기존 결과를 200으로 돌려줍니다.**
유사도 경계값은 운영 중 튜닝하는 설정값이라, 재계산하면 같은 친구와의 관계가
나중에 바뀝니다. 한 번 확정된 관계는 고정합니다.

누가 먼저 코드를 냈는지와 무관하게 **한 쌍당 결과는 하나**입니다. 서로 코드를
교환해도 결과는 하나만 생깁니다.

### 실패하는 경우

없는 코드, 내가 발급한 코드, 나 또는 상대의 페르소나가 없는 경우에는 결과가
생성되지 않습니다. 페르소나가 없는 경우란 회원가입은 했으나 온보딩을 마치지 않아
`persona_elements`가 비어 있는 상태를 말합니다.

---

## 3. GET /api/v1/parallel-relations

내가 참여한 결과 목록을 최신순으로 돌려줍니다. 코드를 뿌린 쪽(A)이 결과를 확인하는
경로입니다.

### Response 200

```json
{
  "relations": [
    {
      "parallelRelationId": "1041",
      "partner": {
        "userId": "77",
        "userName": "서연",
        "profilePhoto": {
          "key": "profile/77/9f3c1a2b.jpg",
          "photoUrl": "https://media.twinly.app/profile/77/9f3c1a2b.jpg?...",
          "position": { "startPos": { "x": 0, "y": 40 }, "width": 300, "height": 300 }
        }
      },
      "relation": "bestFriend",
      "title": "아무때나 전화해도 좋아하는 사이",
      "similarity": 78,
      "createdAt": "2026-08-18T12:11:22+09:00"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `partner` | object | 상대 정보. `ProfilePhotoInfo` 규격은 4번 참고 |
| `partner.userName` | string | 실명(이름). 탈퇴한 상대는 `"탈퇴한 사용자"` |
| `relation` | enum | `enemy` \| `stranger` \| `awkward` \| `close` \| `bestFriend` |
| `similarity` | integer | 0~100 |

결과가 없으면 `relations`는 빈 배열입니다. 페이지네이션은 없습니다.

---

## 4. GET /api/v1/parallel-relations/{parallelRelationId}

### Path Parameter

| 이름 | 타입 | 설명 |
|---|---|---|
| `parallelRelationId` | string(숫자) | 목록·생성 응답에서 받은 값 |

### Response 200

```json
{
  "parallelRelationId": "1041",
  "user": {
    "userId": "12",
    "userName": "지훈",
    "profilePhoto": {
      "key": "profile/12/1b7d4e88.jpg",
      "photoUrl": "https://media.twinly.app/profile/12/1b7d4e88.jpg?...",
      "position": { "startPos": { "x": 0, "y": 0 }, "width": 300, "height": 300 }
    }
  },
  "partner": {
    "userId": "77",
    "userName": "서연",
    "profilePhoto": {
      "key": "profile/77/9f3c1a2b.jpg",
      "photoUrl": "https://media.twinly.app/profile/77/9f3c1a2b.jpg?...",
      "position": { "startPos": { "x": 0, "y": 40 }, "width": 300, "height": 300 }
    }
  },
  "similarity": 78,
  "relation": "bestFriend",
  "title": "아무때나 전화해도 좋아하는 사이",
  "story": "지훈과 서연은 다른 평행우주에서는 상대가 자는지도 안 물어보고 ...",
  "createdAt": "2026-08-18T12:11:22+09:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `user` | object | 요청한 본인 |
| `partner` | object | 상대. 요청자가 누구냐에 따라 서버가 자동으로 갈라 담습니다 |
| `story` | string | 실명과 조사가 치환된 완성 문장 |

`user`, `partner`의 구조는 채팅방·인물 조회 응답과 동일합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | string(숫자) | |
| `userName` | string | 실명(이름). `User.displayGivenName()` 값이며, 탈퇴한 유저는 `"탈퇴한 사용자"` |
| `profilePhoto` | object \| null | 사진 미등록·탈퇴 시 `null` |
| `profilePhoto.key` | string | S3 오브젝트 키 |
| `profilePhoto.photoUrl` | string | CloudFront 서명 URL |
| `profilePhoto.position` | object | 원본 이미지에서 잘라낼 영역 (`startPos.x`, `startPos.y`, `width`, `height`) |

### 이야기(`story`)의 특성

- **저장하지 않고 조회할 때마다 렌더링합니다.** 저장하는 값은 `relation`과
  `similarity`뿐입니다. 관계 등급은 확정되면 경계값을 바꿔도 유지되고, 문구를
  개선하면 과거 결과에도 반영됩니다.
- **이야기에 박히는 이름은 실명입니다.** 유저가 이름을 바꿀 수단이 없으므로
  (프로필 수정에서 바꿀 수 있는 값은 소속뿐입니다) 같은 결과를 다시 열어도
  문장이 흔들리지 않습니다.
- 다만 **상대가 탈퇴하면 이야기 속 이름도 `"탈퇴한 사용자"`로 바뀝니다.**
  ("탈퇴한 사용자와 지훈은 다른 평행우주에서는 ...") 조사는 정상적으로
  붙지만 문장이 어색해지므로, 앱은 `userName`으로 탈퇴 여부를 판단해
  이야기 대신 안내 문구를 보여주는 편이 낫습니다.
- **이야기는 대칭이 아닙니다.** 등장인물 A/B 역할은 저장 시점에 고정되며,
  코드 주인이 A입니다. 두 사람이 보는 이야기는 항상 같습니다.

### 조회 권한

**결과의 당사자 두 명만 조회할 수 있습니다.** 당사자가 아닌 경우와 존재하지 않는
id는 구분하지 않고 같은 응답으로 처리합니다. 구분하면 "그 id는 존재한다"는 사실이
드러나기 때문입니다.

---

# B. 하루 관람

무작위로 배정된 유료 사용자 한 명의 하루치 시뮬레이션을 관람합니다.
무료 사용자에게 시뮬레이션이 어떤 것인지 보여주는 것이 목적입니다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/v1/showcases/today` | 오늘 배정된 관람 대상의 하루치 조회 |

---

## 0. 공통 규칙

인증·ID 문자열·시각 포맷은 **A와 동일합니다.** JWT 액세스 토큰이
필요합니다.

```
Authorization: Bearer {accessToken}
```

---

## 1. GET /api/v1/showcases/today

### Request

파라미터가 없습니다. 관람 대상은 **서버가 정합니다.** 클라이언트가 대상 유저나
날짜를 지정할 수 없습니다.

### 배정 규칙

- 관람 회차는 **오늘(KST)** 입니다. 시뮬레이션은 전날에 미리 돌아 오늘치 결과가
  이미 확정된 상태로 저장되어 있습니다.
- 배정은 **호출자별로 하루에 한 번 확정되고 그대로 고정됩니다.** 같은 날 몇 번을
  호출해도 같은 대상이 나옵니다. 새로고침으로 다른 사람을 계속 보는 것은
  타인의 하루를 열람하는 행위라 허용하지 않습니다.
- 대상 후보는 아래를 모두 만족하는 유저입니다.

| 조건 | 이유 |
|---|---|
| 현재 시즌 참가자 | 시뮬레이션이 도는 유저가 곧 유료 사용자 |
| 해당 회차에 장면이 1건 이상 존재 | 빈 하루는 컨텐츠가 되지 않음 |
| 탈퇴하지 않음 | |
| 호출자 본인이 아님 | |
| 호출자와 차단 관계가 아님(양방향) | |

**전제:** 이 컨텐츠는 유료 사용자의 하루를 제3자에게 보여줍니다. 시즌 참가 시점의
동의 항목에 이 노출이 포함되어야 합니다. 동의 범위를 벗어난 노출은 API 설계로
해결할 수 없습니다.

### Response 200

```json
{
  "showcaseId": "3312",
  "userRef": "1",
  "date": "2026-08-18",
  "serverNow": "2026-08-18T13:20:11+09:00",
  "scenes": [
    {
      "sceneId": "88101",
      "type": "action",
      "startsAt": "2026-08-18T09:00:00+09:00",
      "endsAt": "2026-08-18T09:40:00+09:00",
      "place": "학교 정문",
      "with": [],
      "narration": "지각을 면하려 뛰어서 등교했다.",
      "mind": "오늘도 아슬아슬했다."
    },
    {
      "sceneId": "88102",
      "type": "dialogue",
      "startsAt": "2026-08-18T12:10:00+09:00",
      "endsAt": "2026-08-18T12:50:00+09:00",
      "place": "학생회관 식당",
      "with": ["2"],
      "lines": [
        { "t": "narr", "text": "김OO이 식판을 들고 자리를 찾았다.", "occursAt": "2026-08-18T12:12:00+09:00" },
        { "t": "bubble", "userRef": "2", "action": "웃으며", "text": "여기 앉아.", "occursAt": "2026-08-18T12:13:00+09:00" }
      ]
    }
  ],
  "userInfos": [
    { "userRef": "1", "userName": "김OO", "organization": "한국대학교" },
    { "userRef": "2", "userName": "박OO", "organization": "한국대학교" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `showcaseId` | string(숫자) | 이 배정 건의 ID |
| `userRef` | string(숫자) | 관람 대상의 식별 번호. `userInfos`의 항목과 연결합니다 |
| `date` | string(`YYYY-MM-DD`) | 관람 회차. 항상 오늘(KST) |
| `serverNow` | string(ISO-8601, KST) | 응답 시점의 서버 시각 |
| `scenes` | array | 시간순 정렬. `type`으로 갈리는 두 종류 |
| `userInfos` | array | 관람 대상 + 장면에 등장하는 인물 정보 |

장면과 대사의 구조는 `GET /api/v1/activities/{date}` 와 같습니다. **`scenes`에는
하루 전체가 담깁니다.** 시뮬레이션이 전날에 돌아 오늘치가 이미 확정되어 있기
때문입니다. `serverNow` 를 기준으로 아직 시작하지 않은 장면을 언제 공개할지는
앱이 판단합니다. 본인 조회 API와 같은 방식입니다.

본인 조회 API와 다른 점은 아래 네 가지입니다.

### 관람용으로 달라지는 점

**1. 본문의 이름은 성만 남기고 가립니다.**

장면의 `narration`, `mind`, 대사 `text`·`action`에는 `{user_204}` 형태의
플레이스홀더가 저장되어 있습니다. 본인 조회 API는 이것을 `givenName`(이름)으로
치환하지만, **관람 API는 `familyName + "OO"` 로 치환합니다.**

| API | 치환 결과 |
|---|---|
| `GET /api/v1/activities/{date}` | `민수` |
| `GET /api/v1/showcases/today` | `김OO` |

이름을 아예 지우면 문장이 읽히지 않고, 실명을 그대로 두면 모르는 사람에게 신원이
드러납니다. 성만 남기면 문장이 자연스럽게 읽히면서 특정은 어려워집니다.

치환 대상 유저가 탈퇴했거나 이름을 알 수 없으면 기존 규칙대로 `"탈퇴한 사용자"`가
들어갑니다.

**2. `userInfos`는 이름을 가리고 프로필 사진 대신 소속을 담습니다.**

| 필드 | 타입 | 설명 |
|---|---|---|
| `userRef` | string(숫자) | 서버가 임의로 발급한 번호. 장면의 `with`, 말풍선의 `userRef`, 최상위 `userRef`가 모두 이 값입니다 |
| `userName` | string | `familyName + "OO"`. 탈퇴한 유저는 `"탈퇴한 사용자"` |
| `organization` | string | 소속(학교·회사) |

**프로필 사진(`profilePhoto`)은 내려가지 않습니다.** 얼굴은 성을 가리는 것으로
상쇄되지 않는 식별 정보입니다. 대신 소속을 넣어, 관람자가 "어느 학교의 누군가"
정도의 맥락은 잡을 수 있게 했습니다.

**3. 등장인물은 실제 유저 ID가 아니라 임의로 발급한 `userRef`로 식별합니다.**

본인 조회 API가 `userId`를 쓰는 자리에 전부 `userRef`가 들어갑니다.

| 위치 | 본인 조회 | 관람 |
|---|---|---|
| 장면의 동행·대화 상대 | `with: ["311"]` | `with: ["2"]` |
| 말풍선 화자 | `"userId": "311"` | `"userRef": "2"` |
| 인물 정보 | `userInfos[].userId` | `userInfos[].userRef` |

`userRef`는 **이 응답 안에서만 유효한 번호이며 실제 유저 ID와 대응 관계가
없습니다.** 같은 대상을 다시 배정받아도 같은 번호가 같은 사람을 가리킨다는 보장이
없습니다.

실제 ID를 내리면 관람자가 그 ID로 인물 조회·신고 등 다른 API를 시도할 수 있어,
성을 가린 의미가 사라집니다.

**4. 질문(`questions`)은 내려가지 않습니다.**

질문은 본인이 답해서 다음 회차에 반영하는 상호작용 요소인데, 관람자는 답할 수
없습니다.

### 배정할 대상이 없는 경우

시즌 초반이거나 조건을 만족하는 유저가 전부 차단 관계이면 관람할 대상이 없습니다.
앱은 빈 화면 대신 안내 문구를 띄워야 합니다.
