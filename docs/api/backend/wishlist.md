# 나들이 위시리스트 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [보관한 장소 목록 조회](#places)
- [보관한 장소 상세 조회](#place)
- [보관한 이벤트 목록 조회](#events)
- [보관한 이벤트 상세 조회](#event)
- [코스 보관](#save-course)
- [보관한 코스 목록 조회](#courses)
- [보관한 코스 상세 조회](#course)
- [보관한 장소 제거](#remove-place)
- [보관한 이벤트 제거](#remove-event)
- [보관한 코스 제거](#remove-course)

<a id="places"></a>

## 보관한 장소 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-4"></a>

### v2 — FE - BE (v2), 원본 4행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A4)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
2
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 장소 목록 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/places?location={locationName}&cursor={cursor}
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
  "next_cursor": String,
  "places": {
    {
      "place_id": Integer,
      "name": String,
      "category": String,
      "description": String,
      "address_sg": String
    },
    ...
  }
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="place"></a>

## 보관한 장소 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-5"></a>

### v2 — FE - BE (v2), 원본 5행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A5)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
3
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 장소 상세 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/places/{placeId}
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
    "place_id": Integer,
    "name": String,
    "category": String,
    "address_sg": String,
    "address_road": String,
    "coordinate": {
        "lat": Float,
        "lng": Float,
    },
    "description": String,
    "videos_featured_link": {
        String,
        ...
    },
    "save_date": Date ("yyyy-MM-dd")
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="events"></a>

## 보관한 이벤트 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-6"></a>

### v2 — FE - BE (v2), 원본 6행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A6)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
4
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 이벤트 목록 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/events?ongoing={boolean}&cursor={cursor}
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
  "next_cursor": String,
  "events": {
    {
      "event_id": Integer,
      "name": String,
      "category": String,
      "description": String,
      "start_date": Date,
      "end_date": Date,
      "place" : {
        "name": String,
        "address_sg": String
      }
    },
    ...
  }
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="event"></a>

## 보관한 이벤트 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-7"></a>

### v2 — FE - BE (v2), 원본 7행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A7)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
5
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 이벤트 상세 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/events/{eventId}
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
    "event_id": Integer,
    "name": String,
    "category": String,
    "description": String,
    "videos_featured_link": {
        "https://youtube.com/watch/...",
        ...
    },
    "start_date": Date,
    "end_date": Date,
    "place": {
      "name": String,
      "address_sg": String,
      "address_road": String,
      "coordinate": {
        "lat": Float,
        "lng": Float
      },
    },
    "save_date": Date ("yyyy-MM-dd")
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="save-course"></a>

## 코스 보관

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-14"></a>

### v2 — FE - BE (v2), 원본 14행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A14)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
9
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
개인 보관함에 코스 추가 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/outing-wishlist/outing-courses
```

**요청 본문 (G열)**

```text
{
  "outing_course_id": Integer
}
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
201
```

**응답 본문 (J열)**

```text
없음
```

**항목 설명 (K열)**

```text
Location 헤더는 해당 코스의 보관한 코스 상세 조회 API 주소를 가리킴
```

**발생할 수 있는 오류 코드**

```text
401, 404, 409, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="courses"></a>

## 보관한 코스 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-15"></a>

### v2 — FE - BE (v2), 원본 15행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A15)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
10
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 코스 목록 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/outing-courses/{outingCourseId}?location={locationName}&cursor={cursor}
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
  "next_cursor": String,
  "courses": {
    {
      "course_id": Integer,
      "name": String,
      "address_sg": String,
      "duration_hours": Integer,
      "save_date": Date,
      "stops": {
        "count": Integer,
        "items": {
          {
            "sequence": Integer,
            "stop_id": Integer,
            "name": String,
            "type": String,
            "category": String,
            "start_date": Date,
            "end_date": Date
          },
          ...
        }
      }
    },
    ...
  }
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="course"></a>

## 보관한 코스 상세 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-16"></a>

### v2 — FE - BE (v2), 원본 16행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A16)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
11
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 코스 상세 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/outing-wishlist/outing-courses/{outingCourseId}
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
  "course_id": Integer,
  "name": String,
  "duration_hours": Integer,
  "stops": {
    "count": Integer,
    "items": {
      "sequence": Integer,
      "stop_id": Integer,
      "name": String,
      "type": String,
      "category": String,
      "coordinate": {
        "lat": Float,
        "lng": Float
      },
      "is_available": Boolean
    }
  }
}
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

```text
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="remove-place"></a>

## 보관한 장소 제거

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-18"></a>

### v2 — FE - BE (v2), 원본 18행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A18)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
13
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 장소 제거 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
DELETE /api/v2/member/outing-wishlist/places/{placeId}
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
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="remove-event"></a>

## 보관한 이벤트 제거

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-19"></a>

### v2 — FE - BE (v2), 원본 19행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A19)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
14
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 이벤트 제거 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
DELETE /api/v2/member/outing-wishlist/events/{eventId}
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
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

<a id="remove-course"></a>

## 보관한 코스 제거

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-20"></a>

### v2 — FE - BE (v2), 원본 20행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A20)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
15
```

**버전**

```text
v2
```

**도메인**

```text
개인 보관함
```

**API 엔드포인트 이름**

```text
보관한 여행 코스 제거 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
DELETE /api/v2/member/outing-wishlist/outing-courses/{outingCourseId}
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
401, 404, 422
```

**외부 통신**

미기재

**캐시**

미기재

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| [v2 15행](wishlist.md#backend-v2-row-15) | 코스 목록 경로에 /{outingCourseId} 포함 | [OutingWishlistController](../../../src/main/java/eightjbbm/keepgo/outingwishlist/adapter/in/web/OutingWishlistController.java)의 목록은 GET /api/v2/member/outing-wishlist/outing-courses. ID 경로는 상세 조회다. |
