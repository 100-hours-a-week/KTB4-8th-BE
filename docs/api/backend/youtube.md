# YouTube 연동과 동기화 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [YouTube 동기화](#sync)
- [YouTube 연동](#link)
- [YouTube 연동 정보 조회](#connection)
- [YouTube 연동 해제](#unlink)
- [연결된 서비스 목록 조회](#connections)

<a id="sync"></a>

## YouTube 동기화

| 버전 | 주요 계약 변화 |
| --- | --- |
| v1 | /user/youtube-analyze, 원문에서 보류 상태 |
| v2 | /member/youtube-sync, 204 및 본문 없음 |
| v3 | /member/youtube-sync, SSE accepted·progress·success |

<a id="backend-v1-row-8"></a>

### v1 — FE - BE, 원본 8행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A8)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
6
```

**도메인**

```text
회원
```

**API 엔드포인트 이름**

```text
유튜브 동기화
```

**비고**

```text
보류
```

**API 엔드포인트 주소**

```text
POST /api/v1/user/youtube-analyze
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
409
429
502
```

**외부 통신**

```text
유튜브
```

**캐시**

```text
유튜브 영상의 분석 기록
분석한 유튜브 영상에서 추출한 장소와 이벤트
```

<a id="backend-v2-row-3"></a>

### v2 — FE - BE (v2), 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A3)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
1
```

**버전**

```text
v2
```

**도메인**

미기재

**API 엔드포인트 이름**

```text
유튜브 동기화 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/youtube-sync
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

미기재

**외부 통신**

미기재

**캐시**

미기재

<a id="backend-v3-row-3"></a>

### v3 — FE - BE (v3), 원본 3행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=688668371#gid=688668371&range=A3)

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
유튜브 동기화 API (스트리밍)
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v3/member/youtube-sync
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
(최초 연결 시)
```

**응답 본문 (J열)**

```text
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

id: Integer
event: youtube-sync.accepted
data: {
  "video_analyzed_count": 0,
  "video_fetched_count": Integer
}
```

**항목 설명 (K열)**

```text
최초 연결 시
```

**발생할 수 있는 오류 코드**

미기재

**외부 통신**

미기재

**캐시**

미기재

#### 추가 응답·설명 — 원본 4행

**응답 코드**

```text
-
(SSE)
```

**응답 본문 (J열)**

```text
id: Integer
event: youtube-sync.progress
data: {
  "video_analyzed_count": Integer,
  "video_fetched_count": Integer
}
```

**항목 설명 (K열)**

```text
AI 측 영상 분석 API가 정상 응답을 반환할 때마다 전송
```

#### 추가 응답·설명 — 원본 5행

**응답 코드**

```text
-
(SSE)
```

**응답 본문 (J열)**

```text
id: Integer
event: youtube-sync.success
data: {
  "video_analyzed_count": Integer,
  "video_fetched_count": Integer
}
```

**항목 설명 (K열)**

```text
영상 분석을 모두 마치고 모든 데이터가 정상 저장 되었을 때 전송
```

<a id="link"></a>

## YouTube 연동

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-17"></a>

### v2 — FE - BE (v2), 원본 17행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A17)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
12
```

**버전**

```text
v2
```

**도메인**

```text
유튜브 연동
```

**API 엔드포인트 이름**

```text
유튜브 연동 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
POST /api/v2/member/external-connections/youtube
```

**요청 본문 (G열)**

```text
없음
```

**항목 설명 (H열)**

미기재

**응답 코드**

```text
302
```

**응답 본문 (J열)**

```text
Location: 유튜브 API용 클라이언트 인증 주소 (로그인 불필요)
```

**항목 설명 (K열)**

미기재

**발생할 수 있는 오류 코드**

미기재

**외부 통신**

미기재

**캐시**

미기재

<a id="connection"></a>

## YouTube 연동 정보 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-21"></a>

### v2 — FE - BE (v2), 원본 21행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A21)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
16
```

**버전**

```text
v2
```

**도메인**

```text
유튜브 연동
```

**API 엔드포인트 이름**

```text
유튜브 연동 정보 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/external-connections/youtube
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
    "email": String,
    "status": String,
    "last_job_datetime": Date ("yyyy-MM-dd HH:mm:ss"),
    "videos_fetched_count": Integer,
    "videos_pending_count": Integer
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

<a id="unlink"></a>

## YouTube 연동 해제

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-22"></a>

### v2 — FE - BE (v2), 원본 22행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A22)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
17
```

**버전**

```text
v2
```

**도메인**

```text
유튜브 연동
```

**API 엔드포인트 이름**

```text
유튜브 연동 해제 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
DELETE /api/v2/member/external-connections/youtube
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

미기재

**외부 통신**

미기재

**캐시**

미기재

<a id="connections"></a>

## 연결된 서비스 목록 조회

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-23"></a>

### v2 — FE - BE (v2), 원본 23행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A23)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**번호**

```text
18
```

**버전**

```text
v2
```

**도메인**

```text
유튜브 연동
```

**API 엔드포인트 이름**

```text
연결된 서비스 목록 조회 API
```

**비고**

미기재

**API 엔드포인트 주소**

```text
GET /api/v2/member/external-connections
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
    "services_connected": {
        "name": String,
        "last_job_datetime": Date ("yyyy-MM-dd HH:mm:ss")
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
| [v2 17행](youtube.md#backend-v2-row-17) | YouTube 연동 302 + Location | [InterestExtractionControllerV2](../../../src/main/java/eightjbbm/keepgo/interestextraction/controller/v2/InterestExtractionControllerV2.java)는 200 + LinkYoutubeAccountResponse 본문을 반환한다. |
| [v2 23행](youtube.md#backend-v2-row-23) | services_connected | 같은 컨트롤러의 연결 서비스 응답은 service_connected다. 원문 필드명과 다르며 복수형만으로 배열을 추정하지 않는다. |
| [v3 3행](youtube.md#backend-v3-row-3) | accepted/progress/success만 기재 | [YoutubeSyncConnection](../../../src/main/java/eightjbbm/keepgo/interestextraction/service/youtubesync/YoutubeSyncConnection.java)과 [YoutubeSyncCoordinator](../../../src/main/java/eightjbbm/keepgo/interestextraction/service/youtubesync/YoutubeSyncCoordinator.java)가 실패 종료·재접속 및 진행 집계를 추가로 처리한다. 아래 설명 참조. |

실제 v3 SSE 본문은 video_target_count, video_failed_count, progress도 제공한다. 근거: [YoutubeSyncEvent](../../../src/main/java/eightjbbm/keepgo/interestextraction/service/youtubesync/YoutubeSyncEvent.java). 진행 집계와 종료 조건은 [현재 처리 흐름](../../youtube-analysis-service-flow.md)과 [SLO](../../youtube-sync-slo.md)를 참고한다.
