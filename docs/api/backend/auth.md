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
