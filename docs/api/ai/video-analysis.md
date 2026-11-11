# AI 영상 분석과 장소 검증 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [영상 분석](#analysis)
- [장소 검증](#verification)

<a id="analysis"></a>

## 영상 분석

| 탭 버전 | 주요 계약 변화 |
| --- | --- |
| v1 | video_url 단일 입력과 단일 분석 데이터 |
| v2 | video_urls 배열 입력과 평탄한 data.results 배열 |
| v3 | 분석 결과별 place_type 추가 |

<a id="ai-v1-row-3"></a>

### v1 — AI, 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A3)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
영상 분석
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/analyze-video
```

**요청 본문**

```text
{
    "video_url": "https://youtube.com/shorts/xxxx"
}
```

**요청 설명**

```text
필수
video_url: String
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "analyze_success",
  "data": {
    "place_name": "농민뜨끈이",
    "region": null,
    "category": "맛집",
    "event_start_date": null,
    "event_end_date": null,
    "confidence": 0.95,
    "summary": "..."
  }
}

※ place_name, region, summary는 영상에서 확인되지 않으면 null
※ event_start_date·event_end_date는 category가 팝업·전시일 때만 채워지며, 영상에 명확히 안 나오면 null (verify-place가 웹검색으로 보완)
```

<a id="ai-v2-row-3"></a>

### v2 — AI(v2), 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=2140092047#gid=2140092047&range=A3)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
영상 분석
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/analyze-video
```

**요청 본문**

```text
{
  "video_urls": [
    "https://youtube.com/shorts/aaaa",
    "https://youtube.com/shorts/bbbb",
    "https://youtube.com/shorts/cccc",
    "https://youtube.com/shorts/dddd"
  ]
}

```

**요청 설명**

```text
필수
video_urls: list
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "analyze_success",
  "data": {
    "results": [
      {
        "video_url": "https://youtube.com/shorts/aaaa",
        "status": "success",
        "error": null,
        "place_name": "메가커피 성수점",
        "address": "서울특별시 성동구 아차산로 100",
        "location_detail": "성수역 3번 출구 앞 OO빌딩 1층",
        "category": "카페",
        "business_hours": {
          "timezone": "Asia/Seoul",
          "weekly_hours": {
            "MONDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "TUESDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "WEDNESDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "THURSDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "FRIDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "SATURDAY": [
              { "open_time": "10:00", "close_time": "15:00" },
              { "open_time": "16:00", "close_time": "22:00" }
            ],
            "SUNDAY": []
          },
          "exceptions": {
            "2026-10-20": [],
            "2026-10-21": [
              { "open_time": "10:00", "close_time": "15:00" }
            ]
          },
          "estimated": false
        },
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false,
        "lat": 37.5441,
        "lng": 127.0560,
        "confidence": 0.95,
        "summary": "통유리에 좌석 간격이 넓고 대화 소음이 적음"
      },
      {
        "video_url": "https://youtube.com/shorts/bbbb",
        "status": "success",
        "error": null,
        "place_name": "성천막국수 답십리 본점",
        "address": "서울특별시 동대문구 답십리로 100",
        "location_detail": "답십리역 4번 출구 인근",
        "category": "맛집",
        "business_hours": {
          "timezone": "Asia/Seoul",
          "weekly_hours": {
            "MONDAY": [
              { "open_time": "11:00", "close_time": "21:00" }
            ],
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
            "SUNDAY": null
          },
          "exceptions": {},
          "estimated": false
        },
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false,
        "lat": 37.5665,
        "lng": 127.0560,
        "confidence": 0.95,
        "summary": "메밀 막국수가 대표 메뉴인 노포이고 점심시간에 웨이팅이 있음"
      },
      {
        "video_url": "https://youtube.com/shorts/bbbb",
        "status": "success",
        "error": null,
        "place_name": "답십리 고미술상가",
        "address": "서울특별시 동대문구 고미술로 200",
        "location_detail": null,
        "category": "명소",
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false,
        "lat": 37.5701,
        "lng": 127.0570,
        "confidence": 0.9,
        "summary": "골동품과 빈티지 소품 가게가 모여 있어 구경하며 걷기 좋음"
      },
      {
        "video_url": "https://youtube.com/shorts/cccc",
        "status": "not_found",
        "error": null,
        "place_name": null,
        "address": null,
        "location_detail": null,
        "category": null,
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": null,
        "lat": null,
        "lng": null,
        "confidence": null,
        "summary": null
      },
      {
        "video_url": "https://youtube.com/shorts/dddd",
        "status": "failed",
        "error": "llm_timeout",
        "place_name": null,
        "address": null,
        "location_detail": null,
        "category": null,
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": null,
        "lat": null,
        "lng": null,
        "confidence": null,
        "summary": null
      }
    ]
  }
}



※ results는 요청한 video_urls 순 칭 가능
※ status: success(장소 확인) / not_found(영상 분석은 됐으나 실제 장소를 찾지 못함) / failed(처리 오류, error에 원인
※ 실패나 not_found여도 모든 키는 존재하며 값만 null
※ 동명 장소가 여러 곳이면 지역·특징이 가장 잘 맞는 1곳만 반환
※ event_start_date·event_end_date가 비어 있고 기간 한정 행사로 보이면 웹검색으로 채움. 종료일을 못 찾으면 event_period_
※ 비동기 처리 (스트리밍 도입 여부 미정)
※ summary에는 건물명, 층 같은 위치 정보가 들어감
※ address는 도로명 주소
```

<a id="ai-v3-row-3"></a>

### v3 — AI(v3), 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991&range=A3)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
영상 분석
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/analyze-video
```

**요청 본문**

```text
{
  "video_urls": [
    "https://youtube.com/shorts/aaaa",
    "https://youtube.com/shorts/bbbb",
    "https://youtube.com/shorts/cccc",
    "https://youtube.com/shorts/dddd"
  ]
}

```

**요청 설명**

```text
필수
video_urls: list
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "analyze_success",
  "data": {
    "results": [
      {
        "video_url": "https://youtube.com/shorts/aaaa",
        "status": "success",
        "error": null,
        "place_name": "메가커피 성수점",
        "address": "서울특별시 성동구 아차산로 100",
        "location_detail": "성수역 3번 출구 앞 OO빌딩 1층",
        "category": "카페",
        "business_hours": {
          "timezone": "Asia/Seoul",
          "weekly_hours": {
            "MONDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "TUESDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "WEDNESDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "THURSDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "FRIDAY": [
              { "open_time": "08:00", "close_time": "22:00" }
            ],
            "SATURDAY": [
              { "open_time": "10:00", "close_time": "15:00" },
              { "open_time": "16:00", "close_time": "22:00" }
            ],
            "SUNDAY": []
          },
          "exceptions": {
            "2026-10-20": [],
            "2026-10-21": [
              { "open_time": "10:00", "close_time": "15:00" }
            ]
          },
          "estimated": false
        },
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false,         “place_type” : “장소”,
        "lat": 37.5441,
        "lng": 127.0560,
        "confidence": 0.95,
        "summary": "통유리에 좌석 간격이 넓고 대화 소음이 적음"
      },
      {
        "video_url": "https://youtube.com/shorts/bbbb",
        "status": "success",
        "error": null,
        "place_name": "성천막국수 답십리 본점",
        "address": "서울특별시 동대문구 답십리로 100",
        "location_detail": "답십리역 4번 출구 인근",
        "category": "맛집",
        "business_hours": {
          "timezone": "Asia/Seoul",
          "weekly_hours": {
            "MONDAY": [
              { "open_time": "11:00", "close_time": "21:00" }
            ],
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
            "SUNDAY": null
          },
          "exceptions": {},
          "estimated": false
        },
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false,
        “place_type” : “장소”,
        "lat": 37.5665,
        "lng": 127.0560,
        "confidence": 0.95,
        "summary": "메밀 막국수가 대표 메뉴인 노포이고 점심시간에 웨이팅이 있음"
      },
      {
        "video_url": "https://youtube.com/shorts/bbbb",
        "status": "success",
        "error": null,
        "place_name": "답십리 고미술상가",
        "address": "서울특별시 동대문구 고미술로 200",
        "location_detail": null,
        "category": "명소",
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        “place_type” : “이벤트”,
        "event_period_unresolved": false,
        "lat": 37.5701,
        "lng": 127.0570,
        "confidence": 0.9,
        "summary": "골동품과 빈티지 소품 가게가 모여 있어 구경하며 걷기 좋음"
      },
      {
        "video_url": "https://youtube.com/shorts/cccc",
        "status": "not_found",
        "error": null,
        "place_name": null,
        "address": null,
        "location_detail": null,
        "category": null,
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": null,
        “place_type” : null,
        "lat": null,
        "lng": null,
        "confidence": null,
        "summary": null
      },
      {
        "video_url": "https://youtube.com/shorts/dddd",
        "status": "failed",
        "error": "llm_timeout",
        "place_name": null,
        "address": null,
        "location_detail": null,
        "category": null,
        "business_hours": null,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": null,
        “place_type” : null,
        "lat": null,
        "lng": null,
        "confidence": null,
        "summary": null
      }
    ]
  }
}



※ results는 요청한 video_urls 순 칭 가능
※ status: success(장소 확인) / not_found(영상 분석은 됐으나 실제 장소를 찾지 못함) / failed(처리 오류, error에 원인
※ 실패나 not_found여도 모든 키는 존재하며 값만 null
※ 동명 장소가 여러 곳이면 지역·특징이 가장 잘 맞는 1곳만 반환
※ event_start_date·event_end_date가 비어 있고 기간 한정 행사로 보이면 웹검색으로 채움. 종료일을 못 찾으면 event_period_
※ 비동기 처리 (스트리밍 도입 여부 미정)
※ summary에는 건물명, 층 같은 위치 정보가 들어감
※ address는 도로명 주소
```

<a id="verification"></a>

## 장소 검증

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="ai-v1-row-4"></a>

### v1 — AI, 원본 4행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1878554884#gid=1878554884&range=A4)

원본 메타데이터: A1: 요청 / F1: 응답.

**기능**

```text
장소 검증
```

**요청 메서드**

```text
POST
```

**URL**

```text
/v1/verify-place
```

**요청 본문**

```text
{
  "place_name": "메가커피",
  "region": null,
  "summary": "저가 아메리카노로 유명한 프랜차이즈 카페. 테이크아웃 위주고 좌석은 많지 않음.",
  "event_start_date": null,
  "event_end_date": null
}
```

**요청 설명**

```text
필수
place_name: String

선택
region: String
summary: String
```

**응답 상태 코드**

```text
200
```

**응답 본문·설명**

```text
{
  "message": "verify_success",
  "data": {
    "exists": true,
    "candidates": [
      {
        "place_name": "메가커피 성수점",
        "address": "서울특별시 성동구 성수동2가 1-1",
        "business_hours": "매일 08:00-22:00",
        "lat": 37.5441,
        "lng": 127.0560,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false
      },
      {
        "place_name": "메가커피 건대점",
        "address": "서울특별시 광진구 화양동 2-2",
        "business_hours": "매일 09:00-23:00",
        "lat": 37.5401,
        "lng": 127.0694,
        "event_start_date": null,
        "event_end_date": null,
        "event_period_unresolved": false
      }
    ]
  }
}

※ 후보가 1건이면 원소 1개, 검색 결과가 없으면 exists: false + candidates: []
※ 후보가 다수면 LLM이 신뢰도순으로 정렬해 반환하며, 백엔드는 1번째 원소를 자동 채택해 저장 (나머지는 추후 재선택·확인용으로 보존)
※ event_start_date·event_end_date가 비어있고 summary상 기간 한정 행사로 보이면 웹검색으로 종료일을 찾아 채움. 그래도 못 찾으면 event_period_unresolved: true로 표시
```

#### 추가 응답·설명 — 원본 5행

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

## 현재 구현과 확인 필요 사항

| 항목·출처 | 시트 명세 | 현재 구현·확인 필요 |
| --- | --- | --- |
| 영상 분석 | AI는 video_url 단일 요청, v2·v3는 video_urls 배열과 data.results. v3는 place_type 추가 | [VideoAnalysisJobClient](../../../src/main/java/eightjbbm/keepgo/util/client/ai/job/VideoAnalysisJobClient.java)가 새 평탄 행 응답을 내부 영상별 결과로 묶는다. [DefaultAiCallGateway](../../../src/main/java/eightjbbm/keepgo/util/client/ai/gateway/DefaultAiCallGateway.java)가 `POST /v3/analyze-video`·`videos[]`로 호출하며, `/v1/analyze-video`·`video_urls`를 호출하던 구형 클라이언트는 제거했다(#357). 상세 검증·운영 증거는 [기존 계약 문서](../../ai-video-analysis-contract.md) 참조. |

영상 분석 후주의 잘린 문장을 추측해 완성하지 않는다. 내부 변환·검증·운영 확인은 [AI 영상 분석 HTTP 계약](../../ai-video-analysis-contract.md)을 참고한다.
