# 코스 추천과 탐색 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [코스 추천 요청](#recommend)
- [코스 추천 중단](#cancel)
- [요즘 뜨는 곳 목록 조회](#hot)
- [광고 목록 조회](#ads)
- [장소 상세 조회](#place)
- [이벤트 상세 조회](#event)
- [광고 상세 조회](#ad)
- [진행 중인 이벤트 목록 조회](#ongoing)

<a id="recommend"></a>

## 코스 추천 요청

v1은 일반 HTTP 응답으로 추천 결과를 반환하고, v2는 SSE로 접수·성공·중단을 전달한다. 결과 구조와 오류는 각 버전 계약에서 확인한다.

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-17"></a>

### v1 — FE - BE, 원본 17행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A17)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
13
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
여정 코스 추천 요청
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/recommendation
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
    "metadata": {
        "total_course_count": Integer,
        "location": String,
        "scheduled_time": String
    },
    "snippets": [
        {
            "title": String,
            "recommendation_score": String,
            "total_place_count": Integer,
            "total_travel_time": Integer
        },
        ...
    ],
    "details": [
        {
            "title": String,
            "recommendation_score": String,
            "total_travel_time": Integer
            "items": [
                {
                    "sequence": Integer,
                    "name": String,
                    "category": String,
                    "description": String,
                    "lat": Float,
                    "lng": Float
                },
                ...
            ]
        },
        ...
    ]
}
```

**항목 설명 (J열)**

```text
metadata:
total_course_count:
location:
scheduled_time:

snippets: 코스 후보 목록 표시용
details: 코스 후보 상세 표시용

 - snippet, detail 공통 항목
title:
recommendation_score: 추천도
total_travel_time: 총 소요시간

 - snippet 고유 항목
total_place_count:

 - detail 고유 항목
items:
sequence: 순서
name:
category:
description: ex) "출발지에서 12분 이동"
lat:
lng:

```

**발생할 수 있는 오류 코드 (K열)**

```text
400
401(Access Token 만료)
409
422
429
500
502
503

```

**외부 통신**

```text
AI 서버
```

**캐시**

```text
추천 요청 작업
```

<a id="backend-v2-row-10"></a>

### v2 — FE - BE (v2), 원본 10행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A10)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
7
```

**버전**

```text
v2
```

**도메인**

미기재

**API 엔드포인트 이름**

```text
코스 추천 요청 API (스트리밍)
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/recommendation
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

event: outing-course-recommendation.accepted
id: 123
data:
```

**항목 설명 (K열)**

```text
최초 연결
```

**발생할 수 있는 오류 코드**

미기재

**외부 통신**

미기재

**캐시**

미기재

#### 추가 응답·설명 — 원본 11행

**응답 코드**

```text
없음
```

**응답 본문 (J열)**

```text
event: outing-course-recommendation.success
id: 123
data: {
  "result": {
    "metadata": {
        "total_course_count": Integer,
        "location": String,
        "scheduled_time": String
    },
    "snippets": [
        {
            "title": String,
            "recommendation_score": String,
            "total_place_count": Integer,
            "total_travel_time": Integer
        },
        ...
    ],
    "details": [
        {
            "title": String,
            "recommendation_score": String,
            "total_travel_time": Integer
            "items": [
                {
                    "sequence": Integer,
                    "name": String,
                    "category": String,
                    "description": String,
                    "lat": Float,
                    "lng": Float
                },
                ...
            ]
        },
        ...
    ]
  }
}
```

**항목 설명 (K열)**

```text
응답이 성공적으로 반환되었을 경우
```

#### 추가 응답·설명 — 원본 12행

**응답 코드**

```text
없음
```

**응답 본문 (J열)**

```text
event: outing-course-recommendation.stopped
id: 123
data:
```

**항목 설명 (K열)**

```text
코스 추천 중단 API에 의해 중단되었을 경우
```

<a id="cancel"></a>

## 코스 추천 중단

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-18"></a>

### v1 — FE - BE, 원본 18행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A18)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
14
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
여정 코스 추천 중단
```

**비고**

```text
구현 완료
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/recommendation/cancellation
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
404
500
```

**외부 통신**

```text
AI 서버
```

**캐시**

```text
추천 요청 작업
```

<a id="backend-v2-row-13"></a>

### v2 — FE - BE (v2), 원본 13행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A13)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
8
```

**버전**

```text
v2
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
코스 추천 요청 중단
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/recommendation/cancellation
```

**요청 본문 (G열)**

```text
없음
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
204
```

**응답 본문 (J열)**

```text
없음
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

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
추천 요청 작업
```

<a id="hot"></a>

## 요즘 뜨는 곳 목록 조회

`GET /api/v1/places`는 지원을 종료하여 컨트롤러 매핑을 제거했다. 기존 호출자는 `GET /api/v3/places/hot`으로 전환한다. V1의 sort·size·cursor 기본값 처리, V3 위임 및 리다이렉트는 제공하지 않는다.

<a id="backend-v1-row-19"></a>

### v1 — 지원 종료

기존 `GET /api/v1/places?sort=trending&cursor={cursor}&size={size}`와 더미 `hot_places` 응답은 더 이상 제공하지 않는다. V1 장소 상세 조회(`GET /api/v1/places/{placeId}`)와 인증 정책은 이번 지원 종료의 대상이 아니다.

<a id="backend-v3-row-6"></a>

### v3 — FE - BE (v3), 원본 6행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=688668371#gid=688668371&range=A6)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

미기재

**버전**

```text
v3
```

**도메인**

미기재

**API 엔드포인트 이름**

```text
요즘 뜨는 곳 목록 조회
```

**비고**

DB에 저장된 장소의 name·도로명주소(address)를 places 배열로 반환한다. 장소가 없으면 {"places":[]}를 반환한다. 정렬·페이징은 제공하지 않는다.

**API 엔드포인트 주소**

```text
GET /api/v3/places/hot
```

**요청 본문 (G열)**

```text
없음
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
200
```

**응답 본문 (J열)**

```text
{
  "places" : [
    {
      "name": String,
      "address": String (도로명)
    },
    ...
  ]
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

<a id="ads"></a>

## 광고 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-20"></a>

### v1 — FE - BE, 원본 20행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A20)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
16
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
광고 목록 조회
```

**비고**

```text
더미 데이터
```

**API 엔드포인트 주소**

```text
GET /api/v1/advertisements
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
    "advertisements": [
        {
            "attached_image_url": String,
            "title": String,
            "description": String
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

<a id="place"></a>

## 장소 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-21"></a>

### v1 — FE - BE, 원본 21행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A21)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
17
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
장소 상세 조회
```

**비고**

```text
미구현
```

**API 엔드포인트 주소**

```text
GET /api/v1/places/{placeId}
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
    "message": "준비 중인 기능입니다."
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

<a id="event"></a>

## 이벤트 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-22"></a>

### v1 — FE - BE, 원본 22행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A22)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
18
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
이벤트 상세 조회
```

**비고**

```text
미구현
```

**API 엔드포인트 주소**

```text
GET /api/v1/events/{eventId}
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
    "message": "준비 중인 기능입니다."
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

<a id="ad"></a>

## 광고 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-23"></a>

### v1 — FE - BE, 원본 23행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A23)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
19
```

**도메인**

```text
추천
```

**API 엔드포인트 이름**

```text
광고 상세 조회
```

**비고**

```text
미구현
```

**API 엔드포인트 주소**

```text
GET /api/v1/advertisements/{advertisementId}
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
    "message": "준비 중인 기능입니다."
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

<a id="ongoing"></a>

## 진행 중인 이벤트 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v3-row-7"></a>

### v3 — FE - BE (v3), 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=688668371#gid=688668371&range=A7)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

미기재

**버전**

```text
v3
```

**도메인**

미기재

**API 엔드포인트 이름**

```text
진행 중인 이벤트 목록 조회
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v3/events/ongoing
```

**요청 본문 (G열)**

```text
없음
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
200
```

**응답 본문 (J열)**

```text
{
  "events" : {
    {
      "name": String,
      "address": String (도로명),
      "start_date": Date ("yyyy-MM-dd"),
      "end_date": Date ("yyyy-MM-dd")
    },
    ...
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

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| [v1 19–23행](recommendation.md#backend-v1-row-19) | 더미 목록·미구현 상세 | [RecommendationController](../../../src/main/java/eightjbbm/keepgo/recommendation/controller/RecommendationController.java)도 목록 stub과 준비 중 상세 응답을 제공한다. 실제 조회가 완료된 API로 해석하지 않는다. |
