# KeepGo API 명세

공유 [KeepGo API 명세서](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit)의 6개 탭을 기능 → API 항목 → 버전별 계약 순서로 정리했다. 확인일: 2026-11-01 (Asia/Seoul).

## 기능별 명세

| 통신 | 기능 | 문서 |
| --- | --- | --- |
| BE → AI | AI 영상 분석과 장소 검증 | [명세](ai/video-analysis.md) |
| BE → AI | AI 나들이 조건 추출 | [명세](ai/criteria-extraction.md) |
| BE → AI | AI 코스 추천과 취소 | [명세](ai/course-recommendation.md) |
| BE → AI | AI 임베딩 | [명세](ai/embedding.md) |
| BE → AI | AI 공통 오류 | [명세](ai/common.md) |
| FE → BE | 인증 | [명세](backend/auth.md) |
| FE → BE | 회원과 프로필 | [명세](backend/member.md) |
| FE → BE | YouTube 연동과 동기화 | [명세](backend/youtube.md) |
| FE → BE | 채팅과 나들이 조건 | [명세](backend/chat.md) |
| FE → BE | 코스 추천과 탐색 | [명세](backend/recommendation.md) |
| FE → BE | 나들이 위시리스트 | [명세](backend/wishlist.md) |
| FE → BE | 헬스체크와 SSE 공통 규약 | [명세](backend/common.md) |

## 읽는 방법

각 API 항목 안에서 v1·v2·v3 계약을 비교한다. 총 60개 버전별 API 항목과 추가 응답·SSE 이벤트·공통 오류를 보존했다. 버전이 미기재된 항목은 폐기로 간주하지 않는다. AI의 v1·v2·v3는 탭 구분이며 실제 URL 버전은 각 계약을 따른다.

기능 문서에는 원본 탭·행·메타데이터와 요청·응답·설명·오류·외부 통신·캐시 정보를 보존했다. 시트의 구현 완료·최종 수정일은 현재 배포 검증 결과가 아니다. 코드 블록은 실행 가능한 JSON이 아닌 원문 타입 표기를 포함한다. 빈 셀은 없음·null과 구분한다.

- [공통 해석·인증·오류 및 갱신 절차](implementation-notes.md)
- [AI 영상 분석 HTTP 계약과 운영 확인 기록](../ai-video-analysis-contract.md)
- [도메인 용어](../../GLOSSARY.md)

기능별 현재 구현과 확인 필요 사항은 로컬 소스의 정적 대조 결과다. 실제 서버 호출·배포 검증 결과가 아니다. wire 식별자는 원문을 보존하고 서술에는 도메인 용어를 사용한다. OpenAPI는 이 문서화 범위에 포함하지 않는다.
