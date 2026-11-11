# AI 서버 추천 취소 API(`/v1/recommend-courses/{request_id}/cancel`) 연동

BE는 코스 추천 중단을 AI 서버의 취소 API로 전달하지 않는다. 회원이 중단을 요청하면 BE가 AI 스트림 연결을 닫는 방식으로 취소한다.

## Why this is out of scope

- 스트리밍 추천(`/v3/recommend-course/stream`) 요청에서 AI의 `request_id`를 BE가 얻는 규약이 AI 계약에 없다. BE 작업 ID로 취소 API를 호출하면 AI가 모르는 ID일 가능성이 크다.
- 연결을 닫는 방식은 `POST /api/v3/member/recommendation/cancellation`으로 이미 동작하고, 회원이 보는 결과(추천 중단)는 같다.
- 연동하려면 AI 팀이 `request_id` 전달 규약을 새로 정하고 `/v1` 취소 경로를 `/v3` 계약에 맞춰 유지해야 한다. 이득에 비해 AI 서버와의 계약 결합이 늘어난다.
- 일정 문제로 미루는 것이 아니라, 연결 종료 방식을 채택하기로 한 결정이다.

다시 검토하려면 이 파일을 삭제하고, AI 팀이 확정한 `request_id` 규약을 근거로 새 이슈를 등록한다.

## Prior requests

- #356: "코스 추천 중단 경로 정규화와 request_id 규약 유지 확인"
