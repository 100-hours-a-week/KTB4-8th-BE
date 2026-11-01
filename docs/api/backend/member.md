# 회원과 프로필 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [회원 정보 조회](#get)
- [회원 정보 수정](#update)
- [프로필 사진 업로드](#image)

<a id="get"></a>

## 회원 정보 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-6"></a>

### v1 — FE - BE, 원본 6행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A6)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
4
```

**도메인**

```text
회원
```

**API 엔드포인트 이름**

```text
회원 정보 조회
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
GET /api/v1/user
```

**요청 본문**

```text
없음
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
200
```

**응답 본문**

```text
{
    "nickname": String,
    "email": String,
    "profile_image_url": String,
}
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

미기재

<a id="update"></a>

## 회원 정보 수정

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-7"></a>

### v1 — FE - BE, 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A7)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
5
```

**도메인**

```text
회원
```

**API 엔드포인트 이름**

```text
회원 정보 수정
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
PATCH /api/v1/user
```

**요청 본문**

```text
{
    "nickname": String,
    "profile_image_url": String
}
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
200
```

**응답 본문**

```text
{
    "nickname": String,
    "profile_image_url": String
}
```

**항목 설명 (J열)**

미기재

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
422
500
```

**외부 통신**

미기재

**캐시**

미기재

<a id="image"></a>

## 프로필 사진 업로드

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-9"></a>

### v1 — FE - BE, 원본 9행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A9)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
7
```

**도메인**

```text
기타
```

**API 엔드포인트 이름**

```text
프로필 사진 업로드
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/profile-image
```

**요청 본문**

```text
multipart/form-data
(id: profileImage)
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
201
```

**응답 본문**

```text
Location: String
```

**항목 설명 (J열)**

미기재

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
413
415
500
503
```

**외부 통신**

```text
파일 시스템
```

**캐시**

미기재
