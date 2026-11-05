# YouTube 분석 설계 문서 안내

YouTube 좋아요 영상에서 나들이 가이드를 추출하고 분석 이력을 재사용하며, 실패·재시작 뒤에도 작업을 복구하기 위한 설계 문서다. 문서에 정의한 정책은 구현 완료나 운영 전환 완료를 뜻하지 않는다.

> 2026-10-29: 구현 명세·계획·작업 분할과 ADR 0002는 [#248](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/248)로 대체됐다. 현재 설계는 #248과 [ADR 0003](adr/0003-in-process-analysis-mysql-coordination.md)을 따른다.

## 읽는 순서와 기준

| 문서 | 역할 |
|---|---|
| [도메인 용어](../GLOSSARY.md) | 장소·행사·나들이 가이드의 의미 |
| [구현 명세](youtube-analysis-refactoring-spec.md) | 해결할 문제, 사용자 스토리, 인수 조건과 비목표 |
| [구현 계획](youtube-analysis-refactoring-plan.md) | 최종 설계 결정, 실행·저장·재시도·복구 및 전환 순서 |
| [Guide 동일성과 반복 행사 ADR](adr/0001-guide-identity-and-recurring-events.md) | 유형·이름·도로명주소 동일성과 기존 행사 모델 유지 결정 |
| [분석 실행과 MySQL 조정 ADR](adr/0003-in-process-analysis-mysql-coordination.md) | 분석은 앱 내부에서 실행하고 동기화·임베딩 조정은 MySQL로 하는 결정 |
| [작업 분할](youtube-analysis-refactoring-tickets.md) | #223~#237의 범위, 인수 조건과 직접 의존관계 |

명세의 목적·인수 조건을 기준으로 구현 계획과 ADR의 상세 정책을 적용한다. 서로 충돌하면 구현 전에 문서와 관련 이슈에서 결정을 정리한다. 실제 진행 상태는 [상위 이슈 #222](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/222)와 하위 이슈·PR에서 확인한다. 작업 분할 문서의 체크박스는 작성 시점의 기록이다.

## 초기 자료와 달라진 점

[초기 요구사항](refactor-youtube-sync.md)과 [초기 클래스 설계](refactor-youtube-sync-classes.md)는 설계 배경과 검토 대상을 보존한다. 이 문서에 등장하는 클래스·Outbox 표현을 현재 구현이나 별도 메시지 브로커 도입으로 해석하지 않는다.

초기 요구사항의 후속 요청 기반 복구보다 구현 계획·ADR의 **기동 직후 및 주기적 DB 스캔** 결정을 우선한다. 분석과 임베딩의 실행 상태는 공통 `AiRequestJob`으로 관리하고, 영상별 업무 결과는 `VideoAnalysis`에 별도로 기록한다.

## 구현·배포 경계

- 외부 AI 계약은 [분석 계약 문서](ai-video-analysis-contract.md), MySQL 검증·baseline은 [DB migration 절차](database-migrations.md)와 함께 읽는다.
- 설계 문서 추가만으로 API·DB·Job 실행기가 변경되지는 않는다. 명세에는 아직 구현·검증해야 할 항목이 포함돼 있다.
- 실제 AI 응답, 임베딩 upsert·저장 완료, 운영 MySQL·참조 데이터·외부 벡터 정리와 복원 절차는 해당 환경에서 검증해야 한다. 테스트 환경의 성공을 운영 전환 승인으로 대신하지 않는다.
