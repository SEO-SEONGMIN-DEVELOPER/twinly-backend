# 점검·필수 업데이트 Backend 구현 가이드

앱에 "점검 중" 화면과 "업데이트 필요" 알럿을 띄우기 위한 Backend 작업이다. 이 문서는 Backend가 해야 할 일만 담는다. 범위는 네 가지다.

1. 공통 필터 1개: 점검 중이면 `503 MAINTENANCE`, 앱 버전 미달이면 `426 APP_UPDATE_REQUIRED`
2. 앱 식별 헤더 2개 파싱과 버전 비교
3. probe endpoint 1개: 무인증 `GET /api/v1/app/status` → `200 {}`
4. (2단계) 점검 시작 시 WebSocket `connection.draining` 발행

핵심 원칙 하나: **판정은 서버가 하고, 앱은 status와** `code` **조합을 본다.** 앱은 버전을 비교하지 않고 스토어 URL도 갖고 있지 않다. endpoint별로 손댈 것은 없다. 필터 하나가 전부다.

```text
① iOS·Android 앱 → Backend REST 요청 (설치된 스토어 사용자 버전 헤더 포함)
② 필터: 점검 중?      → 503 { code: "MAINTENANCE", ... }        끝
        버전 미달?     → 426 { code: "APP_UPDATE_REQUIRED", ... } 끝
        아니면        → 컨트롤러로 통과
③ 앱: status + code 조합을 보고 화면 전체를 잠근다
④ 앱: 점검 화면의 [다시 시도] → GET /api/v1/app/status → 정확한 200이면 잠금 해제
```



## 0. 앱은 이렇게 씀

- iOS·Android 앱은 **Backend origin의 모든 REST 요청(refresh 포함)**에 `X-App-Platform`·`X-App-Version` 헤더를 붙인다. web과 presigned S3 같은 외부 origin 요청에는 붙이지 않는다.
- 응답 에러를 파싱하는 공통 지점에서 status와 `code`를 함께 본다. `503 + MAINTENANCE`면 점검 화면, `426 + APP_UPDATE_REQUIRED`면 업데이트 알럿으로 앱 전체를 잠근다. 어느 endpoint에서 왔는지는 보지 않는다.
- 다음 네 시점에 `GET /api/v1/app/status`를 한 번 호출한다: 콜드스타트, 백그라운드 60초 이상 뒤 복귀, 점검 화면의 `다시 시도`, WebSocket `connection.draining(maintenance)` 수신 직후. 응답 body는 읽지 않고 정확한 `200`일 때만 잠금을 푼다.
- probe가 네트워크 오류·timeout·계약 외 HTTP 응답을 받으면 새로 잠그지 않는다(fail-open). 현재 상태가 잠금 없음이면 그대로 열고, 이미 잠금 상태면 정확한 `200`을 받기 전까지 기존 잠금을 유지한다. 앱을 새로 잠그는 건 status와 `code` 조합이 정확히 일치하는 응답뿐이다.
- 상태 코드나 `code` 하나만으로는 판정하지 않는다. `code` 없는 503(배포 중 게이트웨이 503 등)과 다른 status의 동일 code는 일반 오류로 처리한다.

서버 응답별 앱 동작:


| 서버 응답                           | 앱 동작                                       |
| ------------------------------- | ------------------------------------------ |
| `503 MAINTENANCE`               | 점검 화면. `message`·`until` 표시, `다시 시도` 버튼    |
| `426 APP_UPDATE_REQUIRED`       | 업데이트 알럿. `업데이트하기` → `storeUrl` 열기. 닫을 수 없음 |
| `GET /app/status`의 정확한 `200 {}` | 잠금 해제, 앱 재진입                               |
| probe의 4xx·코드 불일치 5xx·네트워크 오류   | 새로 잠그지 않음. 이미 잠금 상태면 기존 잠금 유지              |
| refresh 요청의 `503 MAINTENANCE`   | 로그아웃하지 않음. 점검 화면만 표시                       |




### 판정 순서

```text
점검 중  → 503 (버전과 무관)
버전 미달 → 426
그 외     → 통과
```


### 응답 body

기존 에러 규약([error-handling.md](error-handling.md) §1)의 `{code, message}`에 필드를 더한 형태다.

```jsonc
// HTTP 503, Content-Type: application/json
// Retry-After: 600   (선택. 초 단위. 앱은 표시 참고용으로만 쓴다)
{
  "code": "MAINTENANCE",
  "message": "더 나은 서비스를 위해 점검하고 있어요.",  // string | null
  "until": "2026-09-04T03:00:00+09:00"                // string(ISO 8601 date-time) | null
}
```

```jsonc
// HTTP 426, Content-Type: application/json
{
  "code": "APP_UPDATE_REQUIRED",
  "message": null,                                     // string | null
  "storeUrl": "https://apps.apple.com/kr/app/id0000000000",  // string, non-null
  "minVersion": "0.2.0"                                // string, non-null
}
```


| 필드           | 타입                         | nullable | 어디에 | 비고                                                                   |
| ------------ | -------------------------- | -------- | --- | -------------------------------------------------------------------- |
| `code`       | string                     | 아니오      | 둘 다 | `MAINTENANCE` / `APP_UPDATE_REQUIRED`. 앱은 해당 HTTP status와 정확히 조합해 분기 |
| `message`    | string                     | 예        | 둘 다 | `null`이면 앱 기본 문구. 그대로 사용자에게 노출된다                                     |
| `until`      | string (date-time)         | 예        | 503 | 표시용. `null`이면 예정 시각 행을 숨긴다                                           |
| `storeUrl`   | string (HTTPS)             | 아니오      | 426 | 요청 헤더의 플랫폼에 맞는 스토어 URL                                               |
| `minVersion` | string `major.minor.patch` | 아니오      | 426 | 표시용                                                                  |


- **키는 항상 내린다.** 값이 없으면 키를 빼지 말고 `null`을 넣는다.
- `message`는 사용자에게 그대로 보인다. 개발자용 문구를 넣지 않는다.
- `until`·`minVersion`·`Retry-After`는 표시용이다. 앱이 이 값으로 자동 해제 타이머를 돌리지 않는다.



## 2. 앱 식별 헤더와 버전 판정

앱이 보내는 헤더:


| 헤더               | 값                              | 예       |
| ---------------- | ------------------------------ | ------- |
| `X-App-Platform` | `ios` | `android`              | `ios`   |
| `X-App-Version`  | 스토어 사용자 버전 `major.minor.patch` | `0.1.2` |


판정 규칙:

- **헤더가 없거나 파싱할 수 없으면 통과시킨다.** 헤더를 보내지 않는 구버전 앱은 426을 이해하지 못해 일반 오류만 보게 되므로 막아도 얻는 게 없다. 426 차단은 헤더를 보내는 이 릴리즈 이후 버전부터 실효를 갖는다.
- `X-App-Platform`이 `ios`·`android` 외의 값이면 헤더 없음과 같이 통과시킨다.
- 버전의 SSOT는 플랫폼별 App Store·Play Store에 표시되는 사용자 버전이다. 앱은 기기에 실제 설치된 iOS `CFBundleShortVersionString`·Android `versionName`을 보내고, Backend의 플랫폼별 `minVersion`도 같은 기준으로 관리한다.
- iOS `buildNumber`·Android `versionCode`는 같은 스토어 버전의 제출 빌드를 구분하는 내부 번호이므로 비교하지 않는다. OTA manifest의 버전값도 서버 판정 기준이 아니다.
- 앱이 설치 버전을 읽지 못하거나 형식이 잘못되면 `0.0.0`으로 대체하지 않고 두 헤더를 모두 생략한다. web도 헤더를 보내지 않는다.
- 비교는 `major.minor.patch` 세 자리 숫자 비교다. pre-release 태그·빌드 번호는 없다.
- 최소 버전과 `storeUrl`은 **플랫폼별**로 관리한다. iOS와 Android의 심사·배포 시점이 다르다.
- 점검 판정보다 뒤에 온다. 점검 중이면 버전을 보지 않고 503이다.



## 3. `GET /api/v1/app/status`

```text
무인증 (security: [])
요청 body 없음
200  {}          ← 필터를 통과했다는 뜻. body는 비어 있어도 된다
Cache-Control: no-store
```

- 컨트롤러는 아무 일도 하지 않는다. 의미는 전부 필터가 만든다. 점검 중이면 이 요청도 필터에서 503으로 끝난다.
- 앱이 콜드스타트마다 1회 호출하므로 가볍게, 그리고 **edge/CDN 캐시 없이** 응답해야 한다. 캐시된 200이 나가면 필터를 건너뛰어 점검 중에도 앱이 열린다.
- 앱은 응답 body를 읽지 않는다. 정확한 `200`이면 잠금을 풀고, 그 밖의 HTTP 응답·네트워크 오류면 현재 잠금 상태를 유지한다.
- 기존 endpoint(`legal/policies` 등)를 재사용하지 않고 전용으로 둔다. probe가 다른 API의 캐시·인증·응답 변경에 묶이지 않게 하기 위해서다.



## 4. `connection.draining` 발행 (2단계)

점검 시작을 열린 소켓에도 알려 앱이 즉시 잠기게 하는 단계다. **1~3번 없이 이것만 하면 의미가 없고, 1~3번은 이것 없이도 동작한다.** 발행이 없으면 앱은 다음 REST 호출의 503으로 잠기며, 그 지연을 없애는 것이 이 단계의 목적이다.

- 시점: 점검 플래그를 켠 직후, 세션을 닫기 전.
- 대상: 모든 active STOMP 세션.
- destination과 body는 기존 realtime 계약([new-backend-data-contract.md](new-backend-data-contract.md) `RealtimeServerControl`) 그대로다. wire 변경은 없다.

```jsonc
// destination: /user/queue/connection/control
{
  "v": 1,
  "kind": "control",
  "type": "connection.draining",
  "payload": {
    "reason": "maintenance",     // 점검이면 exact "maintenance". deploy/overload/realtime_disabled는 앱을 잠그지 않는다
    "retryAfterMs": 600000       // integer 0..900000 | null | 생략
  }
}
```

- 발행 → 짧은 유예 → 세션 close 순서다. 앱은 이 메시지로 직접 잠그지 않고 `GET /api/v1/app/status`를 즉시 호출한 뒤 그 503으로 잠근다. 따라서 발행 시점에 필터가 이미 켜져 있어야 한다.
- 계약대로 `reason`이 `maintenance`가 아니면 앱은 기존 degraded → REST 복구 → 재연결 동작만 한다.



## 5. ErrorCode와 문서

- `com.nidus.twinly.common.web.ErrorCode`에 `MAINTENANCE`, `APP_UPDATE_REQUIRED`를 추가한다. 이 enum이 [error-handling.md](error-handling.md)의 SSOT라 카탈로그도 같이 갱신한다.
- OpenAPI: `components/responses`에 `503 Maintenance`·`426 UpgradeRequired`를 한 번 정의하고 "모든 endpoint 공통" 설명을 둔다. operation마다 `$ref`를 붙일지는 선택이다. `GET /api/v1/app/status`와 두 요청 헤더도 추가한다.
- stage 배포 뒤 live OpenAPI로 앱 팀이 대조한다.



## 6. 설정값 관리 (권장)

Backend가 정할 일이지만 앱 입장에서 필요한 조건만 적는다.


| 설정      | 내용                         | 조건                                                                                                                           |
| ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| 점검 플래그  | on/off, `message`, `until` | **재배포 없이** 켜고 끌 수 있어야 한다. 점검은 배포 없이 시작되는 경우가 많다                                                                              |
| 최소 버전   | 플랫폼별 `minVersion`          | 재배포 없이 변경 가능하면 좋다                                                                                                            |
| 스토어 URL | 플랫폼별 `storeUrl`            | HTTPS. iOS는 `https://apps.apple.com/kr/app/id...`, Android는 `https://play.google.com/store/apps/details?id=com.nidus.twinly` |


DB 한 행이든 설정 서버든 admin API든 상관없다. 다만 값이 바뀌면 필터가 즉시 반영해야 한다(프로세스 캐시면 TTL 짧게).

## 7. 검증

stage에서 curl로 확인한다. 앱 없이 필터만으로 검증 가능하다.

```bash
# 정상: 200 {}. Cache-Control: no-store 확인
curl -i https://stage-api.trytwinly.com/api/v1/app/status \
  -H 'X-App-Platform: ios' -H 'X-App-Version: 0.1.2'
```

```bash
# 점검 플래그 ON 뒤: 503 + code MAINTENANCE (버전과 무관, 헤더 없어도 503)
curl -i https://stage-api.trytwinly.com/api/v1/app/status
```

```bash
# 최소 버전을 0.2.0으로 올린 뒤: 426 + code APP_UPDATE_REQUIRED + storeUrl
curl -i https://stage-api.trytwinly.com/api/v1/app/status \
  -H 'X-App-Platform: android' -H 'X-App-Version: 0.1.2'
```

체크리스트:

- [ ] 점검 ON에서 `/api/v1/**` 어느 경로를 쳐도 503 + `MAINTENANCE`
- [ ] 점검 ON에서 인증 토큰 refresh도 503 + `MAINTENANCE` (401이 아님)
- [ ] 점검 ON에서 만료·위조 토큰으로 인증 endpoint를 쳐도 401이 아니라 503 + `MAINTENANCE` (필터가 Security보다 앞)
- [ ] 503·426 body가 Boot 기본 오류 형식(`status`/`error`/`path`)이 아니라 `code`가 있는 우리 형식
- [ ] 점검 OFF, 버전 미달 → 426. `storeUrl`·`minVersion` non-null
- [ ] 헤더 없음 또는 `X-App-Platform`이 `ios`·`android` 외 값 → 통과
- [ ] `message`·`until`을 비웠을 때 키가 `null`로 존재
- [ ] 두 응답 모두 `Content-Type: application/json`
- [ ] (2단계) 점검 ON 시 열린 STOMP 세션에 `connection.draining` `reason: "maintenance"` 도착 후 close



## 8. 주의사항

- **배포 중 503에** `code`**를 넣지 않는다.** 롤링 배포 중 게이트웨이가 내는 503은 `code` 없이 그대로 두면 앱이 일반 오류로 처리한다. 필터의 503만 `MAINTENANCE`를 가진다. 반대로 점검 페이지를 ingress에서 만든다면 HTTP 503과 body의 `MAINTENANCE`가 함께 있어야 앱이 잠긴다.
- `REALTIME_UNAVAILABLE`**과 별개다.** ticket 발급의 `503 REALTIME_UNAVAILABLE`은 realtime만 불가하다는 뜻이고 앱은 이걸로 잠그지 않는다. 점검 필터는 그보다 앞에서 `MAINTENANCE`를 내린다.
- **refresh도 503.** 점검 중 `/api/v1/auth/refresh`가 503 `MAINTENANCE`를 받아도 앱은 토큰을 지우지 않는다. 필터가 refresh를 예외로 둘 필요는 없다. 다만 401·`INVALID_REFRESH_TOKEN`으로 바꿔 내리면 사용자가 로그아웃되므로 하면 안 된다.
- `message`**는 사용자 노출 문구다.** 한국어, 존댓말, 두 문장 이내. ㅇ

