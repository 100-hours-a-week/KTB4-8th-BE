# 인증 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [구글 OAuth 로그인](#login)
- [로그아웃](#logout)
- [토큰 재발급](#refresh)

<a id="login"></a>

## 구글 OAuth 로그인

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-3"></a>

### v1 — FE - BE, 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A3)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
1
```

**도메인**

```text
인증/인가
```

**API 엔드포인트 이름**

```text
구글 OAuth 로그인
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/auth-session
```

**요청 본문**

```text
{
    "provider": "google"
}
```

**항목 설명 (G열)**

```text
provider: OAuth 인증 서비스 제공자
```

**응답 코드 (H열)**

```text
201
```

**응답 본문**

```text
{
    "access_token": String,
    "token_type": "Bearer",
    "expires_in": Integer
}
```

**항목 설명 (J열)**

```text
Access Token은 전달 후 Authorization 헤더에 Bearer로 담기
Refresh Token은 쿠키로 전달
```

**발생할 수 있는 오류 코드 (K열)**

```text
400
500
502
```

**외부 통신**

```text
구글
```

**캐시**

```text
RT 해시
```

<a id="login-redirect"></a>

### 로그인 성공 리다이렉트 (Fragment 계약)

원본 시트에 없는 항목이며 [OidcLoginSuccessHandler](../../../src/main/java/eightjbbm/keepgo/auth/OidcLoginSuccessHandler.java) 구현을 기준으로 한다.

Google OIDC 인증이 성공하면 백엔드는 `keepgo.security.redirection-address`(환경 변수 `FRONTEND_SERVER_ADDRESS`)로 리다이렉트하며, Access Token은 쿼리가 아닌 Fragment로 전달한다.

```text
{redirection-address}#accessToken=<JWT>&expiresIn=1800&initialLogin=<true|false>
```

- 목적지의 경로와 기존 쿼리는 보존한다. 목적지에 이미 Fragment가 있으면 위 값으로 대체한다.
- Access Token을 쿼리에 함께 싣지 않는다. Refresh Token은 기존대로 `HttpOnly`, `Secure`, `SameSite=Lax`, Path `/api/v1/user/auth-session` 쿠키로 전달한다.
- 프론트엔드는 Fragment에서 세 필드를 파싱하고, 토큰을 읽은 직후 `history.replaceState`로 Fragment를 제거해야 한다.
- 이전 계약(`?accessToken=...&expiresIn=...&initialLogin=...`)에서 바뀐 호환성 변경이다. 프론트엔드가 새 계약을 처리하는지 확인한 뒤 백엔드를 배포한다.

남는 위험: Fragment는 HTTP 요청 URL과 `Referer` 헤더에는 실리지 않아 요청 로그와 외부 전송 노출을 줄이지만, 방문 기록이나 주소창에 남을 수 있고 페이지 JavaScript와 권한 있는 확장 프로그램이 읽을 수 있다. 백엔드의 `Location` 응답 헤더에는 토큰이 포함되므로 이 헤더를 수집하는 프록시·로그에도 노출될 수 있다. 일회용 코드 교환 방식은 이번 범위에 포함하지 않는다.

<a id="logout"></a>

## 로그아웃

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-4"></a>

### v1 — FE - BE, 원본 4행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A4)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
2
```

**도메인**

```text
인증/인가
```

**API 엔드포인트 이름**

```text
로그아웃
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
DELETE /api/v1/user/auth-session
```

**요청 본문**

```text
없음
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
204
```

**응답 본문**

```text
없음
```

**항목 설명 (J열)**

미기재

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
500
```

**외부 통신**

미기재

**캐시**

```text
폐기된 AT?
```

<a id="refresh"></a>

## 토큰 재발급

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-5"></a>

### v1 — FE - BE, 원본 5행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A5)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
3
```

**도메인**

```text
인증/인가
```

**API 엔드포인트 이름**

```text
AT 재발급
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/auth-session/refresh
```

**요청 본문**

```text
없음
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
201
```

**응답 본문**

```text
{
    "access_token": String,
    "token_type": "Bearer",
    "expires_in": Integer
}
```

**항목 설명 (J열)**

```text
Access Token은 전달 후 Authorization 헤더에 Bearer로 담기
Refresh Token은 쿠키로 전달
```

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Refresh Token 만료)
500
502
```

**외부 통신**

```text
구글
```

**캐시**

```text
폐기된 AT?
폐기된 RT?
```

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| [v1 3행](auth.md#backend-v1-row-3) | 로그인 201, 토큰 본문 | [AuthController](../../../src/main/java/eightjbbm/keepgo/auth/AuthController.java)의 로그인은 303과 Location: /oauth2/authorization/google. 최종 OAuth 성공 응답과 최초 로그인 요청을 구분해야 한다. |
