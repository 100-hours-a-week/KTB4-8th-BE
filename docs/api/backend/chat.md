# 채팅과 나들이 조건 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [채팅 전송](#send)
- [나들이 조건 수정](#criteria)
- [챗봇 응답 조회](#response)
- [채팅 내역 조회](#history)
- [채팅 내역 초기화](#clear)

<a id="send"></a>

## 채팅 전송

v1은 202와 Location을 반환하고 별도 API로 응답을 폴링한다. v2는 같은 기능의 결과를 SSE 이벤트로 전달한다. 각 버전의 요청·응답은 아래 계약을 따른다.

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-10"></a>

### v1 — FE - BE, 원본 10행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A10)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
8
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
채팅 전송
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/chat-messages
```

**요청 본문**

```text
{
    "content": String
}
```

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

```text
202
```

**응답 본문**

```text
Location: /api/v1/user/chat-messages/{chatId}/response

{
    "content": String,
    "is_by_bot": false
}
```

**항목 설명 (J열)**

```text
폴링 방식으로 조회
```

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
409
422
500
503
```

**외부 통신**

```text
AI 서버
```

**캐시**

```text
대답 생성 작업
```

<a id="backend-v2-row-8"></a>

### v2 — FE - BE (v2), 원본 8행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A8)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
6
```

**버전**

```text
v2
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
채팅 전송 API (스트리밍)
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/chat-messages
```

**요청 본문 (G열)**

```text
EventSource가 아닌 fetch?
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
200
(최초 연결 시)
```

**응답 본문 (J열)**

```text
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

event: chat-reply.accepted
id: 123
data:  {
  "chat": {
    "content": String,
    "is_by_bot": false
  }
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

미기재

**외부 통신**

미기재

**캐시**

미기재

#### 추가 응답·설명 — 원본 9행

**응답 코드**

```text
없음
```

**응답 본문 (J열)**

```text
event: chat-reply.success
id: 123
data: {
  "chat": {
    "content": String,
    "is_by_bot": true
  },
  "slot": {
    "coordinate": {
      "lat": Float,
      "lng": Float
    },
    "location_name_requested": String,
    "time_requested_datetime": Date ("yyyy-MM-dd HH:mm:ss"),
    "time_available_hours": Integer,
    "categories": List<String>
  }
}
```

<a id="criteria"></a>

## 나들이 조건 수정

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-11"></a>

### v1 — FE - BE, 원본 11행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A11)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
9
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
의도 카드 업데이트
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
PATCH /api/v1/user/chat-messages/slot
```

**요청 본문**

```text
{
    "user_location": {
        "lat": Float,
        "lng": Float,
    },
    "requested_location_name": String,
    "requested_date_time": Date,
    "available_time": Integer,
    "categories": {
        String, ...
    }
}
```

**항목 설명 (G열)**

```text
lat: 사용자의 위도
lng: 사용자의 경도
location: 희망하는 지역
scheduled_time_slot: 희망하는 날짜/시간대
available_time_duration: 외출 가능 시간
category: 카테고리(카페, 팝업, ...)
```

**응답 코드 (H열)**

```text
200
```

**응답 본문**

```text
{
    "user_location_name": String
}
```

**항목 설명 (J열)**

```text
기존과 다른 주소를 입력했을 경우
```

**발생할 수 있는 오류 코드 (K열)**

```text
400
401
422
500
```

**외부 통신**

미기재

**캐시**

```text
의도 카드
```

#### 추가 응답·설명 — 원본 12행

**응답 코드 (H열)**

```text
204
```

**응답 본문**

```text
없음
```

**항목 설명 (J열)**

```text
주소의 변동이 없는 입력일 경우
```

<a id="response"></a>

## 챗봇 응답 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-13"></a>

### v1 — FE - BE, 원본 13행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A13)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
10
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
챗봇 응답 조회
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
GET /api/v1/user/chat-messages/{chatId}/response
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
    "status": String,
    "poll_after": Integer
}
```

**항목 설명 (J열)**

미기재

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
404
500
```

**외부 통신**

```text
AI 서버
```

**캐시**

```text
대답 생성 작업
의도 카드
```

#### 추가 응답·설명 — 원본 14행

**응답 코드 (H열)**

```text
200
```

**응답 본문**

```text
{
    "status": String,
    "chat": {
        "content": String,
        "is_by_bot": true
    },
    "slot": {
        "origin": {
            "lat": Float,
            "lng": Float
        },
        "region": String,
        "datetime": String,
        "available_time": Integer,
        "category": List<String>
    }
}
```

<a id="history"></a>

## 채팅 내역 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-15"></a>

### v1 — FE - BE, 원본 15행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A15)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
11
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
채팅 내역 조회
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
GET /api/v1/user/chat-messages?cursor={cursor}&size={size}
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
    "hasNext": Boolean,
    "nextCursor": Integer,
    "chats": [
        {
            "content": String,
            "is_by_bot": Boolean
        },
        ...
    ]
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

<a id="clear"></a>

## 채팅 내역 초기화

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-16"></a>

### v1 — FE - BE, 원본 16행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A16)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
12
```

**도메인**

```text
채팅
```

**API 엔드포인트 이름**

```text
채팅 내역 초기화
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
DELETE /api/v1/user/chat-messages
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

미기재

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| [v1 11행](chat.md#backend-v1-row-11) | 지역 변동 시 200 및 user_location_name 객체 | [ChatController](../../../src/main/java/eightjbbm/keepgo/chat/controller/ChatController.java)는 지역명이 있으면 String 본문, 없으면 204. 시트의 객체 형태와 다르다. |
