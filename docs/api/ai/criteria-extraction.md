# AI 나들이 조건 추출 API 명세

확인일: 2026-11-09 (Asia/Seoul). v3는 스트리밍으로 대체되었고 이전 v3 내용은 git 이력에서 확인한다. [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [나들이 조건 추출](#extraction)

<a id="extraction"></a>

## 나들이 조건 추출

<a id="ai-v1-row-6"></a>

### v1 — AI, 원본 6행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A6)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
슬롯 추출
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/extract
```

**요청 본문**

```text
{
  "chat": "조용한 카페 위주로 두세 시간 정도 볼 거야",
  "date": "2026-09-01",
  "prev_slot": {
    "origin": null,
    "region": "성수",
    "datetime": "2026-09-06",
    "available_time": null,
    "category": null
  },
  "prev_query": null
}
```

**요청 설명**

```text

필수
chat:String,
date:date,
prev_slot:dict,

선택
prev_query:String

다만 슬롯 내부 요소들은 전부
string 이며 null 값이 될 수 있다
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "extract_success",
  "data": {
    "slot": {
      "origin": null,
      "region": "성수",
      "datetime": "2026-09-06",
      "available_time": "180",
      "category": "카페"
    },
    "query": "조용한",
    "bot_message": "성수에서 토요일에 조용한 카페 위주로 3시간 코스 찾아볼게요."
  }
}

※ available_time은 문자열 "180"(3시간) / "360"(6시간) / "540"(9시간) 중 하나
```

<a id="ai-v2-row-4"></a>

### v2 — AI(v2), 원본 4행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A4)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
슬롯 추출
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/extract
```

**요청 본문**

```text
{
  "chat": "조용한 카페 위주로 두세 시간 정도 볼 거야",
  "date": "2026-09-01",
  "prev_slot": {
    "origin": null,
    "region": "성수",
    "datetime": "2026-09-06 00:00:00",
    "available_time": null,
    "category": null
  },
  "prev_query": null
}
```

**요청 설명**

미기재

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "extract_success",
  "data": {
    "slot": {
      "origin": null,
      "region": "성수",
      "datetime": "2026-09-06 00:00:00",
      "available_time": "180",
      "category": "카페"
    },
    "query": "조용한",
    "bot_message": "성수에서 토요일에 조용한 카페 위주로 3시간 코스 찾아볼게요."
  }
}

※ available_time은 문자열 "180"(3시간) / "360"(6시간) / "540"(9시간) 중 하나
```

<a id="ai-v3-row-5"></a>

### v3 — AI(v3), 원본 5행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A5)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
슬롯 추출 스트리밍
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v3/extract/stream
```

**요청 본문**

```text
{
  "chat": "조용한 카페 위주로 두세 시간 정도 볼 거야",
  "date": "2026-09-01",
  "prev_slot": {
    "origin": null,
    "region": "성수",
    "datetime": "2026-09-06 00:00:00",
    "available_time": null,
    "category": null
  },
  "prev_query": null
}
```

**요청 설명**

미기재

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "event": "delta",
  "data": {"text": "성수에서 토요일에 조용한 카페 "}
}

{
  "event": "delta",
  "data": {"text": "위주로 3시간 코스 찾아볼게요."}
}

{
  "event": "done",
  "data": {
    "slot": {
      "origin": null,
      "region": "성수",
      "datetime": "2026-09-06 00:00:00",
      "available_time": "180",
      "category": ["카페"]
    },
    "query": "조용한",
    "bot_message": "성수에서 토요일에 조용한 카페 위주로 3시간 코스 찾아볼게요."
  }
}

{
  "event": "error",
  "data": {"message": "llm_timeout"}
}

※ delta: text를 순서대로 이어 붙여 화면에 표시
※ done: 저장에는 done의 slot, query, bot_message를 사용
※ error message: llm_timeout / llm_rate_limited / llm_invalid_response / internal_server_error
※ done이나 error를 보내면 연결 종료. 둘 다 없이 끊기면 실패로 보고 새로 요청
```

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| 나들이 조건 추출 category | v3 done 이벤트의 slot.category는 배열(["카페"]). v1·v2 예시는 문자열 | [ExtractSlotRequestV2](../../../src/main/java/eightjbbm/keepgo/chat/service/dto/ExtractSlotRequestV2.java)와 [ExtractSlotResponseV2](../../../src/main/java/eightjbbm/keepgo/chat/service/dto/ExtractSlotResponseV2.java)는 Set&lt;String&gt;, 즉 배열이다. v3 스트리밍 응답과는 형태가 맞지만 v1·v2의 문자열 예시와는 다르다. 요청 prev_slot.origin도 좌표 객체다. |
| 나들이 조건 추출 응답 방식 | v3는 /v3/extract/stream SSE(delta·done·error). v1·v2는 단일 JSON | [AiServerExtractSlotApiService](../../../src/main/java/eightjbbm/keepgo/chat/service/AiServerExtractSlotApiService.java)가 공통 수신기 [AiSseClient](../../../src/main/java/eightjbbm/keepgo/util/client/ai/sse/AiSseClient.java)로 /v3/extract/stream을 호출한다. delta는 도착하는 대로 FE에 중계(/api/v3 채팅)하고, 저장에는 done의 slot·query·bot_message만 쓴다. error 이벤트나 done 없는 끊김에서는 나들이 조건과 봇 채팅을 저장하지 않는다. 채팅 서비스가 이 경로를 쓴다. `/v1/extract`를 호출하던 구형 클라이언트는 제거했다(#357). 타임아웃·재시도 설정은 추가하지 않았다. |
| 나들이 조건 추출 datetime | AI는 날짜만, v2·v3는 공백 구분 일시 예시 | 위 DTO는 LocalDateTime이며 요청 prev_slot.datetime과 done의 slot.datetime 모두 `yyyy-MM-dd HH:mm:ss`(공백 구분)로 직렬화·해석한다. 날짜만인 v1과는 호환되지 않는다. |
