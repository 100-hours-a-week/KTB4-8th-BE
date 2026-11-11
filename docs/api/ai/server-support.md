# AI 서버 API 버전별 지원 현황

확인일: 2026-11-10 (Asia/Seoul). 기준: [KTB4-8th-AI](https://github.com/100-hours-a-week/KTB4-8th-AI) `develop` 브랜치 `e355d38`(2026-11-10)의 라우트 정의(`app/api/*.py`). [전체 목차](../README.md)

시트의 AI(v3) 탭 명세와 AI 저장소의 실제 라우트를 비교했다. 코드에 라우트가 없으면 미지원으로 적는다. 배포된 서버가 `develop`과 같다는 보장은 없으므로, 배포 환경은 `GET /v3/version`이 구현된 뒤 확인한다.

## 지원 현황

| 기능 | v1 경로 | v3 시트 경로 | AI 저장소 구현 | 판단 |
| --- | --- | --- | --- | --- |
| 영상 분석 | `POST /v1/analyze-video` | `POST /v3/analyze-video` (`videos[]`, `closed` 상태, `place_type`) | `/v1/analyze-video`만 있다. 요청은 `video_urls`다. 응답에는 `place_type`·`closed`가 이미 있다. | v3 경로·`videos[]` 요청 **미구현**. `upload_date`·`title`·`description`을 받는 필드가 없다. |
| 조건 추출 | `POST /v1/extract` | `POST /v3/extract/stream` (SSE) | 둘 다 있다. | 지원. BE는 `/v3/extract/stream`으로 호출한다(#354). |
| 코스 추천 | `POST /v1/recommend-courses` | `POST v3/recommend-course/stream` (SSE) | 둘 다 있다. 스트리밍 경로는 `/v3/recommend-course/stream`(단수 `course`)이다. | 지원. 시트의 경로는 `recommend-course`이므로 복수형과 혼동하지 않는다. |
| 코스 추천 중단 | `POST /v1/recommend-courses/{request_id}/cancel` | 시트도 `/v1` 경로 유지 | `/v1` 경로가 있다. | 지원. v3 경로는 없다. |
| 임베딩 저장 | `POST /v1/embed-places` | `POST /v3/embed-places` | `/v1/embed-places`만 있다. | v3 경로 **미구현** |
| 임베딩 삭제 | `POST /v1/embed-places/delete` | 시트도 `/v1` 경로 유지 | `/v1` 경로가 있다. | 지원 |
| 챗봇 예열 | 없음 | `POST /v3/warmip` | 라우트가 없다. | **미구현**. 경로가 `warmip`(오타 가능성)이다. |
| 버전 정보 | 없음 | `GET v3/version` | 라우트가 없다. `GET /health`만 있다. | **미구현** |

## 정리

- AI 저장소에서 `/v3`로 동작하는 것은 `POST /v3/extract/stream`, `POST /v3/recommend-course/stream` 두 스트리밍 경로뿐이다.
- 시트가 v3로 적은 `analyze-video`, `embed-places`, `warmip`, `version`은 아직 AI 저장소에 없다. BE는 이 경로를 호출하면 404가 날 수 있다. 호출 전환 시점은 AI 팀과 맞춘다.
- 조건 추출은 BE(#354)가 `/v3/extract/stream`으로 전환했고 `/v1/extract` fallback은 없다. AI 서버가 이 경로를 지원하지 않으면 채팅이 실패한다.
- 영상 분석은 BE(#350, PR #358)가 `/v3/analyze-video`·`videos[]`로 전환했다. AI가 `/v3`를 배포하기 전에 BE를 배포하면 영상 분석이 전부 실패한다.
- 시트에서 `cancel`과 `embed-places/delete`는 v3 탭에서도 `/v1` 경로다. v3로 옮긴 것이 아니다.
- 시트 경로 표기가 일관되지 않다: `/v3/warmip`, `v3/version`, `v3/recommend-course/stream`은 선행 `/`가 없거나 오타 가능성이 있다. 원문은 [각 명세](video-analysis.md)에 보존한다.
