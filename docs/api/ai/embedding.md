# AI 임베딩 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [임베딩 저장](#save)
- [임베딩 삭제](#delete)

<a id="save"></a>

## 임베딩 저장

<a id="ai-v1-row-10"></a>

### v1 — AI, 원본 10행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A10)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 저장
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places
```

**요청 본문**

```text
{
  "places": [
    { "place_id": "p001", "place_name": "성수 A카페", "summary": "통유리에 좌석 간격 넓고 대화 소음 적음" },
    { "place_id": "p002", "place_name": "성수 B카페", "summary": "디저트 유명, 주말 웨이팅 길고 붐빔" }
  ]
}
```

**요청 설명**

```text
필수
places : dict (최대 100개)

※ 백엔드가 DB에 저장해 place_id가 확정된 뒤 호출
※ 같은 place_id로 재호출 시 덮어씀 (upsert)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_success",
  "data": {
    "embedded_count": 2
  }
}
```

<a id="ai-v2-row-8"></a>

### v2 — AI(v2), 원본 8행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A8)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 저장
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places
```

**요청 본문**

```text
{
  "places": [
    { "place_id": "p001", "place_name": "성수 A카페", "summary": "통유리에 좌석 간격 넓고 대화 소음 적음" },
    { "place_id": "p002", "place_name": "성수 B카페", "summary": "디저트 유명, 주말 웨이팅 길고 붐빔" }
  ]
}
```

**요청 설명**

```text
필수
places : dict (최대 100개)

※ 백엔드가 DB에 저장해 place_id가 확정된 뒤 호출
※ 같은 place_id로 재호출 시 덮어씀 (upsert)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_success",
  "data": {
    "embedded_count": 2
  }
}
```

<a id="ai-v3-row-8"></a>

### v3 — AI(v3), 원본 8행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A8)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 저장
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places
```

**요청 본문**

```text
{
  "places": [
    { "place_id": "p001", "place_name": "성수 A카페", "summary": "통유리에 좌석 간격 넓고 대화 소음 적음" },
    { "place_id": "p002", "place_name": "성수 B카페", "summary": "디저트 유명, 주말 웨이팅 길고 붐빔" }
  ]
}
```

**요청 설명**

```text
필수
places : dict (최대 100개)

※ 백엔드가 DB에 저장해 place_id가 확정된 뒤 호출
※ 같은 place_id로 재호출 시 덮어씀 (upsert)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_success",
  "data": {
    "embedded_count": 2
  }
}
```

<a id="delete"></a>

## 임베딩 삭제

<a id="ai-v1-row-11"></a>

### v1 — AI, 원본 11행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A11)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 삭제
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places/delete
```

**요청 본문**

```text
{
  "place_ids": [
    "p001", "p002"
  ]
}
```

**요청 설명**

```text
필수
place_ids: String 리스트 (최대 100개)
※ 존재하지 않는 place_id가 포함돼도 무시하고 나머지만 삭제
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_delete_success", "data": {"deleted_count": 2}
}
```

<a id="ai-v2-row-9"></a>

### v2 — AI(v2), 원본 9행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A9)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 삭제
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places/delete
```

**요청 본문**

```text
{
  "place_ids": [
    "p001", "p002"
  ]
}
```

**요청 설명**

```text
필수
place_ids: String 리스트 (최대 100개)
※ 존재하지 않는 place_id가 포함돼도 무시하고 나머지만 삭제
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_delete_success", "data": {"deleted_count": 2}
}
```

<a id="ai-v3-row-9"></a>

### v3 — AI(v3), 원본 9행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A9)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 임베딩 삭제
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/embed-places/delete
```

**요청 본문**

```text
{
  "place_ids": [
    "p001", "p002"
  ]
}
```

**요청 설명**

```text
필수
place_ids: String 리스트 (최대 100개)
※ 존재하지 않는 place_id가 포함돼도 무시하고 나머지만 삭제
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "embed_delete_success", "data": {"deleted_count": 2}
}
```

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| 임베딩 저장 | place_id 기준 upsert, embedded_count 응답 | [AiServerService](../../../src/main/java/eightjbbm/keepgo/util/client/ai/analysis/AiServerService.java)가 `POST /v3/embed-places`로 장소 하나씩 보내고 응답의 `success`만 확인한다(#360). 시트 예시의 `/v1/embed-places`·`places[]`·`embedded_count`와 다르며, 구계약 응답은 성공으로 보지 않는다. 실제 저장 완료 의미는 2026-10-29 사용자 확인 기록을 보존한다. |
| 임베딩 삭제 | POST /v1/embed-places/delete | 원문에는 있으나 해당 HTTP 경로를 호출하는 운영 클라이언트는 이번 검색에서 확인하지 못했다. 구현·배포 여부 확인 필요. |
