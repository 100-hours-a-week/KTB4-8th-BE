# AI 코스 추천과 취소 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [코스 추천](#recommendation)
- [코스 추천 취소](#cancellation)

<a id="recommendation"></a>

## 코스 추천

<a id="ai-v1-row-7"></a>

### v1 — AI, 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A7)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses
```

**요청 본문**

```text
{
  "query": "조용한",
  "candidates": [
    {
      "place_id": "p001",
      "place_name": "성수 A카페",
      "summary": "통유리에 좌석 간격 넓고 대화 소음 적음",
      "lat": 37.5445,
      "lng": 127.0557,
      "business_hours": "매일 10:00-22:00"
    },
    {
      "place_id": "p003",
      "place_name": "성수 books",
      "summary": "책 읽는 손님 위주라 대화 소음이 적음",
      "lat": 37.5462,
      "lng": 127.0538,
      "business_hours": "매일 11:00-21:00 (월요일 휴무)"
    }
  ],
  "history_place_ids": [
    { "place_id": "p010", "saved_at": "2026-08-20" },
    { "place_id": "p022", "saved_at": "2026-08-25" }
  ],
  "available_time": "180",
  "category": "카페",
  "datetime": "2026-09-06 14:00",
  "origin": { "lat": 37.5445, "lng": 127.0557 }
}
```

**요청 설명**

```text
필수
query : String
candidates : dict (최대 50개)

선택
history_place_ids : dict (최대 50개)
available_time : String ("180"/"360"/"540" 중 하나)
category : String (카페/팝업/전시/맛집/명소/기타 중 하나)
datetime : String
origin : dict

※ candidates는 백엔드가 좌표 반경으로 1차 필터링한 목록
※ available_time은 문자열 "180"(3시간) / "360"(6시간) / "540"(9시간) 중 하나
※ history_place_ids는 최근 저장한 장소 목록(취향 벡터 계산용). 비어있으면 query만으로 검색
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "recommend_success",
  "data": {
    "courses": [
      {
        "title": "성수 조용한 카페 산책",
        "places": [
          {
            "place_id": "p001",
            "place_name": "성수 A카페",
            "travel_minutes": 0,
            "arrival_time": "14:00",
            "stay_minutes": 60,
            "reason": "좌석 간격이 넓고 소음이 적어 조용한 조건에 부합"
          },
          {
            "place_id": "p003",
            "place_name": "성수 books",
            "travel_minutes": 8,
            "arrival_time": "15:08",
            "stay_minutes": 70,
            "reason": "책 읽는 손님 위주라 오래 머물기 좋음"
          }
        ],
        "total_duration_minutes": 138
      }
    ]
  }
}
```

#### 추가 응답·설명 — 원본 8행

**응답 상태 코드**

```text
502
```

**응답 본문·설명**

```text
{
  "message": "map_api_error", "data": null
}
```

<a id="ai-v2-row-5"></a>

### v2 — AI(v2), 원본 5행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A5)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses
```

**요청 본문**

```text
{
  "query": "조용한",
  "candidates": [
    {
      "place_id": "p001”,
      "place_name": "성수 A카페",
      "summary": "통유리에 좌석 간격 넓고 대화 소음 적음",
      "lat": 37.5445,
      "lng": 127.0557,
      "business_hours": {
        "timezone": "Asia/Seoul",
        "weekly_hours": {
          "MONDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "TUESDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "WEDNESDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "THURSDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "FRIDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "SATURDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "SUNDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ]
        },
        "exceptions": {},
        "estimated": false
      }
    },
    {
      "place_id": "p003",
      "place_name": "성수 books",
      "summary": "책 읽는 손님 위주라 대화 소음이 적음",
      "lat": 37.5462,
      "lng": 127.0538,
      "business_hours": {
        "timezone": "Asia/Seoul",
        "weekly_hours": {
          "MONDAY": [],
          "TUESDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "WEDNESDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "THURSDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "FRIDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "SATURDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "SUNDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ]
        },
        "exceptions": {
          "2026-09-06": [
            { "open_time": "13:00", "close_time": "18:00" }
          ]
        },
        "estimated": false
      }
    }
  ],
  "available_time": "180",
  "category": ["카페"],
  "datetime": "2026-09-06T14:00:00",
  "origin": { "lat": 37.5445, "lng": 127.0557 }
}

```

**요청 설명**

```text
필수
query : String
candidates : dict (최대 50개)

선택
history_place_ids : dict (최대 50개)
available_time : String ("180"/"360"/"540" 중 하나)
category : String 리스트 (카페/팝업/전시/맛집/명소/기타 중 하나)
datetime : datetime
origin : dict

※ candidates는 백엔드가 좌표 반경으로 1차 필터링한 목록
※ available_time은 문자열 "180"(3시간) / "360"(6시간) / "540"(9시간) 중 하나
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "recommend_success",
  "data": {
    “error” : null,
    “match_score” : 74
    "courses": [
      {
        "title": "성수 조용한 카페 산책",
        "places": [
          {
            "place_id": "p001”,
            "place_name": "성수 A카페",
            "travel_minutes": 0,
            "arrival_time": "14:00",
            "stay_minutes": 60,
            "reason": "좌석 간격이 넓고 소음이 적어 조용한 조건에 부합"
          },
          {
            "place_id": "p003”,
            "place_name": "성수 books",
            "travel_minutes": 8,
            "arrival_time": "15:08",
            "stay_minutes": 70,
            "reason": "책 읽는 손님 위주라 오래 머물기 좋음"
          }
        ],
        "total_duration_minutes": 138
      }
    ]
  }
}

{
  "message": "recommend_not_found",
  "data": {
    “error” : null,
    “match_score” : null
    "courses": []
  }
}

{
  "message": "recommend_cancel_accepted",
  "data": {
    “error” : null,
    “match_score” : null
    "courses": []
  }
}

{
  "message": "recommend_failed”,
  "data": {
    “error” : "llm_timeout",
    “match_score” :  null
    "courses": []
  }
}

```

#### 추가 응답·설명 — 원본 6행

**응답 상태 코드**

```text
502
```

**응답 본문·설명**

```text
{
  "message": "map_api_error", "data": null
}
```

<a id="ai-v3-row-5"></a>

### v3 — AI(v3), 원본 5행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A5)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses
```

**요청 본문**

```text
{
  "query": "조용한",
  "candidates": [
    {
      "place_id": "p001”,
      "place_name": "성수 A카페",
      "summary": "통유리에 좌석 간격 넓고 대화 소음 적음",
      "lat": 37.5445,
      "lng": 127.0557,
      "business_hours": {
        "timezone": "Asia/Seoul",
        "weekly_hours": {
          "MONDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "TUESDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "WEDNESDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "THURSDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "FRIDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "SATURDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ],
          "SUNDAY": [
            { "open_time": "10:00", "close_time": "22:00" }
          ]
        },
        "exceptions": {},
        "estimated": false
      }
    },
    {
      "place_id": "p003",
      "place_name": "성수 books",
      "summary": "책 읽는 손님 위주라 대화 소음이 적음",
      "lat": 37.5462,
      "lng": 127.0538,
      "business_hours": {
        "timezone": "Asia/Seoul",
        "weekly_hours": {
          "MONDAY": [],
          "TUESDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "WEDNESDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "THURSDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "FRIDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "SATURDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ],
          "SUNDAY": [
            { "open_time": "11:00", "close_time": "21:00" }
          ]
        },
        "exceptions": {
          "2026-09-06": [
            { "open_time": "13:00", "close_time": "18:00" }
          ]
        },
        "estimated": false
      }
    }
  ],
  "available_time": "180",
  "category": ["카페"],
  "datetime": "2026-09-06T14:00:00",
  "origin": { "lat": 37.5445, "lng": 127.0557 }
}

```

**요청 설명**

```text
필수
query : String
candidates : dict (최대 50개)

선택
history_place_ids : dict (최대 50개)
available_time : String ("180"/"360"/"540" 중 하나)
category : String 리스트 (카페/팝업/전시/맛집/명소/기타 중 하나)
datetime : datetime
origin : dict

※ candidates는 백엔드가 좌표 반경으로 1차 필터링한 목록
※ available_time은 문자열 "180"(3시간) / "360"(6시간) / "540"(9시간) 중 하나
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "recommend_success",
  "data": {
    “error” : null,
    “match_score” : 74
    "courses": [
      {
        "title": "성수 조용한 카페 산책",
        "places": [
          {
            "place_id": "p001”,
            "place_name": "성수 A카페",
            "travel_minutes": 0,
            "arrival_time": "14:00",
            "stay_minutes": 60,
            "reason": "좌석 간격이 넓고 소음이 적어 조용한 조건에 부합"
          },
          {
            "place_id": "p003”,
            "place_name": "성수 books",
            "travel_minutes": 8,
            "arrival_time": "15:08",
            "stay_minutes": 70,
            "reason": "책 읽는 손님 위주라 오래 머물기 좋음"
          }
        ],
        "total_duration_minutes": 138
      }
    ]
  }
}

{
  "message": "recommend_not_found",
  "data": {
    “error” : null,
    “match_score” : null
    "courses": []
  }
}

{
  "message": "recommend_cancel_accepted",
  "data": {
    “error” : null,
    “match_score” : null
    "courses": []
  }
}

{
  "message": "recommend_failed”,
  "data": {
    “error” : "llm_timeout",
    “match_score” :  null
    "courses": []
  }
}

```

#### 추가 응답·설명 — 원본 6행

**응답 상태 코드**

```text
502
```

**응답 본문·설명**

```text
{
  "message": "map_api_error", "data": null
}
```

<a id="ai-v3-stream"></a>

### v3 스트리밍 — BE 구현 기준

시트의 v3 탭 URL `v3/recommend-course/stream`은 앞 슬래시가 없는 표기이므로 BE는 `POST /v3/recommend-course/stream`으로 정규화해 호출한다. 응답은 `text/event-stream`이며 이벤트마다 `event: <이름>`과 JSON `data:`가 온다. 공통 수신 규칙은 [AiSseClient](../../../src/main/java/eightjbbm/keepgo/util/client/ai/sse/AiSseClient.java)를 따른다.

| 이벤트 | 본문 | BE 처리 |
| --- | --- | --- |
| `status` | `{"stage": String}` | `stage`를 값 그대로 FE에 전달한다. 알 수 없는 값도 막지 않는다. |
| `done` | `{"message", "data": {"error", "courses"}}` | `message`로 결과를 판정한다. 코스는 여기서 한 번에 받는다. |
| `error` | `{"message": String}` | `llm_timeout`·`llm_rate_limited`·`llm_invalid_response`는 502(`AI_LLM_*`), 그 밖은 503으로 변환한다. |

`done.message` 판정:

| message | 결과 |
| --- | --- |
| `recommend_success`, `recommend_not_found` | 성공. `data.error`가 null이고 `data.courses`가 있어야 한다. |
| `recommend_cancel_accepted` | 중단(`stopped`). |
| `recommend_failed` | 실패. `data.error`가 `llm_*`이면 502, 아니면 503. |
| 그 밖의 값, `data` 누락 | 실패(503). |

- HTTP 200만으로 성공으로 보지 않는다.
- `match_score`는 `data.courses[]` 각 코스에서 읽는다. 없으면 기본값 85를 쓴다. v2 탭의 `data.match_score`(전체 값)는 읽지 않는다.
- 요청 `candidates[].place_id`는 정수다. 응답 `places[].place_id`는 정수 또는 숫자 문자열을 받아 정수로 해석한다.
- `done`·`error` 없이 연결이 끊기면 실패로 처리하고 회원이 다시 요청할 수 있다.
- 취소 시 BE가 AI 연결을 직접 닫는다. 아래 `/v1/recommend-courses/{request_id}/cancel`은 호출하지 않는다.

<a id="cancellation"></a>

## 코스 추천 취소

<a id="ai-v1-row-9"></a>

### v1 — AI, 원본 9행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A9)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천 중단
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses/{request_id}/cancel
```

**요청 본문**

```text
-
```

**요청 설명**

```text
필수
request_id: String(path parameter)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "cancel_accepted",
  "data": null
}
```

<a id="ai-v2-row-7"></a>

### v2 — AI(v2), 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A7)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천 중단
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses/{request_id}/cancel
```

**요청 본문**

```text
-
```

**요청 설명**

```text
필수
request_id: String(path parameter)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "cancel_accepted",
  "data": null
}
```

<a id="ai-v3-row-7"></a>

### v3 — AI(v3), 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A7)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
코스 추천 중단
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/recommend-courses/{request_id}/cancel
```

**요청 본문**

```text
-
```

**요청 설명**

```text
필수
request_id: String(path parameter)
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "cancel_accepted",
  "data": null
}
```

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| 추천 요청 | v2·v3의 category 배열, available_time 문자열, business_hours 객체 | [RecommendCoursesRequestV2](../../../src/main/java/eightjbbm/keepgo/recommendation/service/dto/RecommendCoursesRequestV2.java)가 `POST /v3/recommend-course/stream`으로 보낸다. 후보 `place_id`는 정수다. 구형 [RecommendCourseRequest](../../../src/main/java/eightjbbm/keepgo/util/dto/RecommendCourseRequest.java)는 availableTime 정수, businessHours 문자열이며 후보 생성 시 좌표·영업시간이 null이다. v1 호출 경로(`/api/v1/user/recommendation`)에 남아 있으며 이번 전환의 대상이 아니다. |
| 추천 응답 | v3는 SSE `status`·`done`·`error`, `done`의 data.error와 여러 message 값 | [AiServerRecommendApiService](../../../src/main/java/eightjbbm/keepgo/recommendation/service/AiServerRecommendApiService.java)가 `done` 본문을 읽고, [RecommendationServiceV2](../../../src/main/java/eightjbbm/keepgo/recommendation/service/RecommendationServiceV2.java)가 `message`로 판정한다([v3 스트리밍](#ai-v3-stream)). HTTP 200만으로 업무 성공을 판정하지 않는다. `match_score`는 코스별이다. 시트 예시는 `data.match_score`(전체 값)이므로 AI 팀 확인이 필요하다. |
| 추천 취소 | request_id 경로, 취소 응답은 있지만 원 요청에서 ID 전달 규약 미기재 | BE는 AI의 `/v1` cancel을 호출하지 않고 스트림 연결을 닫아 취소한다. 취소 규약은 별도 이슈(#356)에서 정한다. |

AI(v2)·AI(v3)는 query를 필수로 적지만 현재 RecommendCoursesRequestV2는 nullable로 선언한다. 선택 항목 history_place_ids는 현재 신형 DTO에 없다. 원문 예시는 스마트 따옴표·누락 쉼표를 포함하며, 후보 배열을 설명에서 dict로 표기한 부분도 있다.
