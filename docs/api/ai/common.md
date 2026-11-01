# AI 공통 오류 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [공통 오류](#errors)

<a id="errors"></a>

## 공통 오류

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="ai-v1-row-18"></a>

### v1 — AI, 원본 18행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A18)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
공통 오류
```

**URL**

```text
전체 공통
```

**요청 설명**

```text
모든 엔드포인트에 공통 적용
```

**응답 상태 코드**

```text
400
```

**응답 본문·설명**

```text
{
  "message": "invalid_request",
  "data": null
}
```

#### 추가 명세 — 원본 19행

**응답 상태 코드**

```text
422
```

**응답 본문·설명**

```text
{
  "message": "validation_error", "data": null
}
```

#### 추가 명세 — 원본 20행

**응답 상태 코드**

```text
429
```

**응답 본문·설명**

```text
{
  "message": "llm_rate_limited", "data": null
}
```

#### 추가 명세 — 원본 21행

**응답 상태 코드**

```text
500
```

**응답 본문·설명**

```text
{
  "message": "internal_server_error",
  "data": null
}
```

#### 추가 명세 — 원본 22행

**응답 상태 코드**

```text
502
```

**응답 본문·설명**

```text
{
  "message": "llm_invalid_response", "data": null
}
```

#### 추가 명세 — 원본 23행

**응답 상태 코드**

```text
504
```

**응답 본문·설명**

```text
{
  "message": "llm_timeout", "data": null
}
```
