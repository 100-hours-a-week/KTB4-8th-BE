# AI 서버 API 버전별 지원 현황

확인일: 2026-11-11 (Asia/Seoul). 기준: [KTB4-8th-AI](https://github.com/100-hours-a-week/KTB4-8th-AI) `develop` 브랜치 `e355d38`(2026-11-10)의 라우트 정의(`app/api/*.py`). 2026-11-11에도 `develop` 최신 커밋이 같음을 확인했다. [전체 목차](../README.md)

시트의 AI(v3) 탭 명세와 AI 저장소의 실제 라우트, BE의 호출 코드를 비교했다. 코드에 라우트가 없으면 미지원으로 적는다. 배포된 서버가 `develop`과 같다는 보장은 없으므로, 배포 환경은 AI 서버에 `GET /v3/version`이 구현된 뒤 확인한다.

## 지원 현황

| 기능 | v1 경로 | v3 시트 경로 | AI 저장소 구현 | BE 구현 |
| --- | --- | --- | --- | --- |
| 영상 분석 | `POST /v1/analyze-video` | `POST /v3/analyze-video` (`videos[]`, `closed` 상태, `place_type`) | `/v1/analyze-video`만 있다. 요청은 `video_urls`다. 응답에는 `place_type`·`closed`가 이미 있다. | `/v3/analyze-video`·`videos[]`로 호출한다(#350). `/v1/analyze-video` 호출 코드는 제거했다(#357). AI 저장소에 v3 경로가 없으므로 배포 전 AI 팀과 맞춘다. |
| 조건 추출 | `POST /v1/extract` | `POST /v3/extract/stream` (SSE) | 둘 다 있다. | `/v3/extract/stream`으로 호출한다(#354). `/v1/extract` 호출 코드는 제거했다(#357). |
| 코스 추천 | `POST /v1/recommend-courses` | `POST v3/recommend-course/stream` (SSE) | 둘 다 있다. 스트리밍 경로는 `/v3/recommend-course/stream`(단수 `course`)이다. | `/api/v2`·`/api/v3` 추천은 `/v3/recommend-course/stream`으로 호출한다(#355). `/api/v1/user/recommendation`은 아직 `/v1/recommend-courses`를 호출한다. |
| 코스 추천 중단 | `POST /v1/recommend-courses/{request_id}/cancel` | 시트도 `/v1` 경로 유지 | `/v1` 경로가 있다. v3 경로는 없다. | `/api/v2`·`/api/v3` 중단은 스트림 연결을 닫는다(#355, #356). `/api/v1/user/recommendation/cancellation`은 아직 `/v1` cancel을 호출한다. |
| 임베딩 저장 | `POST /v1/embed-places` | `POST /v3/embed-places` | `/v1/embed-places`만 있다. | `/v3/embed-places`로 호출한다(#360). AI 저장소에 v3 경로가 없으므로 배포 전 AI 팀과 맞춘다. |
| 임베딩 삭제 | `POST /v1/embed-places/delete` | 시트도 `/v1` 경로 유지 | `/v1` 경로가 있다. | 호출 코드 없음 |
| 챗봇 예열 | 없음 | `POST /v3/warmup` | 라우트가 없다. | `AiServerApiClient.warmUp()`이 `POST /v3/warmup`을 호출한다(#361). 호출하는 서비스 코드는 아직 없다. |
| 버전 정보 | 없음 | `GET /v3/version` | 라우트가 없다. `GET /health`만 있다. | `AiServerApiClient.getVersion()`이 `GET /v3/version`을 호출한다(#361). 호출하는 서비스 코드는 아직 없다. |

## 정리

- AI 저장소에서 `/v3`로 동작하는 것은 `POST /v3/extract/stream`, `POST /v3/recommend-course/stream` 두 스트리밍 경로뿐이다.
- 시트가 v3로 적은 `analyze-video`, `embed-places`, `warmup`, `version`은 아직 AI 저장소에 없다. BE는 `analyze-video`와 `embed-places`를 이미 호출하므로 AI 서버가 지원하기 전에 배포하면 404가 난다. `warmup`과 `version`은 클라이언트 메서드만 있고 호출하는 서비스 코드가 없다. 호출 전환 시점은 AI 팀과 맞춘다.
- 조건 추출·영상 분석·임베딩 저장은 `/v1` fallback이 없다. AI 서버가 v3 경로를 지원하지 않으면 해당 기능이 실패한다.
- 코스 추천은 `/api/v1/user/recommendation`만 `/v1/recommend-courses`를 아직 호출하고, 취소는 `/api/v1/user/recommendation/cancellation`만 `/v1/recommend-courses/{jobId}/cancel`을 아직 호출한다. 이 호출은 FE가 v1 API를 쓰는 동안 남으며 `RecommendationService`, `AiServerApiClient`, `util/dto/RecommendCourse*`가 함께 남는다. #356이 AI 취소 연동을 채택하지 않고 `.out-of-scope`에 기록한 것과 이 v1 취소 호출은 어긋나며, 제거는 [#370](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/370)에서 추적한다. `done` 본문의 `match_score` 위치(코스 단위인지 `data` 단위인지)는 AI 팀의 실제 샘플로 확인한다. [코스 추천 명세](course-recommendation.md)
- 시트에서 `cancel`과 `embed-places/delete`는 v3 탭에서도 `/v1` 경로다. v3로 옮긴 것이 아니다.
- 시트 경로 표기가 일관되지 않다: `v3/recommend-course/stream`은 선행 `/`가 없다. 원문은 [각 명세](video-analysis.md)에 보존한다.
