# Android 로그인 연동 가이드 (Custom Tabs)

> 대상: FixLog Android 클라이언트
> Base URL: `https://fixlog.art/fixlog` (로컬: `http://localhost:8080/fixlog`)

WebView 로그인은 Google 정책상 차단되므로(`disallowed_useragent`) 시스템 브라우저(Custom Tabs)로 로그인한다.
딥링크로는 **토큰 대신 1회용 코드만** 돌아오고, 토큰은 별도 POST로 교환한다.

---

## 전체 흐름

```
1. 앱   : Custom Tab 으로 GET /login/app?redirect_uri=kr.co.fixlog://oauth2callback&state={nonce}
2. 서버 : 302 → Google 로그인 (시스템 브라우저)
3. 서버 : 302 → kr.co.fixlog://oauth2callback?code={app_code}&state={nonce}
4. 앱   : state 가 1에서 보낸 nonce 와 같은지 검증
5. 앱   : POST /auth/exchange { "code": "{app_code}" } → accessToken, refreshToken
6. 앱   : 이후 요청에 Authorization: Bearer {accessToken}
```

---

## 1. 로그인 시작 — `GET /login/app`

**인증 불필요.** Custom Tab 으로 연다.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `redirect_uri` | O | `kr.co.fixlog://oauth2callback` 고정. 다른 값은 `400 INVALID_REQUEST` |
| `state` | - | 앱이 매 로그인마다 새로 만드는 nonce. 그대로 되돌아오므로 CSRF 검증에 쓴다 |

> `redirect_uri` 는 문자열이 **정확히** 일치해야 한다. 끝 슬래시·대소문자·쿼리 추가 모두 거부된다.

## 2. 복귀 딥링크

성공
```
kr.co.fixlog://oauth2callback?code={app_code}&state={state}
```
실패
```
kr.co.fixlog://oauth2callback?error={errorCode}&state={state}
```
`errorCode` 는 Google 이 내려준 OAuth 에러 코드(`access_denied` 등), 판별이 안 되면 `login_failed`.

> 딥링크에 `accessToken`/`refreshToken` 은 실리지 않는다.
> `app_code` 는 **수명 60초, 1회용**이다. 받는 즉시 3번으로 교환하고, 앱에 저장하지 않는다.

## 3. 코드 교환 — `POST /auth/exchange`

**인증 불필요.**

Request
```json
{ "code": "{app_code}" }
```

Response (200)
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci..."
  }
}
```

실패 (400)
```json
{ "code": "INVALID_REQUEST", "message": "유효하지 않거나 만료된 code입니다." }
```
코드가 없거나·만료됐거나·이미 사용된 경우 모두 같은 응답이다. 로그인을 1번부터 다시 시작하면 된다.

---

## 4. 이후 API — 기존과 동일

| | |
|---|---|
| 토큰 재발급 | `POST /auth/token/refresh` — `{ "refreshToken": "..." }` → `{ "accessToken": "..." }` |
| 현재 사용자 | `GET /auth/token` — `Authorization: Bearer {accessToken}` |
| accessToken 수명 | 1시간 |
| refreshToken 수명 | 14일 |

---

## 안드로이드 설정 메모

- Google Cloud OAuth 설정은 서버 콜백 URL 그대로다. **앱 스킴을 Google 쪽에 등록할 필요 없다.**
- 딥링크 수신을 위해 매니페스트에 intent-filter 를 등록한다.

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="kr.co.fixlog" android:host="oauth2callback" />
</intent-filter>
```

- 로그인 왕복은 **브라우저 세션 쿠키**로 이어진다. Custom Tab 대신 시크릿 탭이나 쿠키를 지우는 브라우저로 열면 복귀에 실패한다.

---

## 서버 설정 (백엔드 참고)

```properties
# 허용 딥링크 — 콤마로 여러 개 등록 가능
oauth2.app-redirect-uris=kr.co.fixlog://oauth2callback
# 1회용 코드 수명(초)
oauth2.app-code-ttl-seconds=60
```

> 1회용 코드는 서버 인메모리에 보관된다. OAuth 인가 자체가 이미 `HttpSession`(인메모리)에 의존하므로,
> 서버를 여러 대로 늘릴 때는 세션과 코드 저장소를 함께 외부 저장소(Redis 등)로 옮겨야 한다.
