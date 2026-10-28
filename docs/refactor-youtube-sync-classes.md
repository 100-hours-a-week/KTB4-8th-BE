# YouTube 분석 리팩토링 초기 클래스 설계

> 초기 설계 자료다. 최종 정책과 문서별 적용 순서는 [문서 안내](youtube-analysis-refactoring.md)를 따른다. 현재 구현 완료를 의미하지 않는다.

AI 요청의 실행 관리는 공통화하고 영상 분석·임베딩의 업무 처리는 유형별 Handler가 담당한다. 기존 클래스의 수정·대체 대상도 포함한다.

### 전체 클래스 목록

| 영역 | 클래스 | 책임 |
|---|---|---|
| 분석 진입 | `YoutubeVideoAnalyzer` | `List<Video>` → `List<OutingGuide>` 분석 계약 |
| | `DefaultYoutubeVideoAnalyzer` | 입력 검사·중복 제거, 작업 확보, 실행 요청, 완료 대기, 결과 통합 |
| | `VideoAnalysisWorkService` | 최신 이력 재사용·활성 작업 공유·신규 등록 결정. 등록 경쟁 제어 및 최대 10개 배치 구성 |
| 공통 AI 실행 | `AiRequestJob` | 재시도·소유권 인계를 포함한 논리적 AI 작업의 영속 실행 상태 관리 |
| | `AiRequestJobService` | 작업 등록, 실행권 확보, heartbeat, 호출 횟수 기록, 재시도 예약, 만료 복구 및 종료 조율 |
| | `AiRequestJobExecutor` | 대기 작업 실행, 유형별 병렬도 제한, heartbeat·기한 처리 구동 |
| | `AiRequestJobHandler` | 유형별 작업 수행 및 최종 실패 처리 계약 |
| | `AiRequestRetryPolicy` | 오류별 재시도 여부, `Retry-After`·backoff·jitter를 반영한 대기 시간 계산 |
| 영상 분석 처리 | `VideoAnalysisJobHandler` | 영상 분석 AI 호출, 응답 변환·검증, 저장 재시도 조율 |
| | `VideoAnalysisResultService` | 배치 결과·Guide·매핑·임베딩 작업·Job 완료를 원자적으로 저장. 최종 실패 시 분석 종료와 활성 등록 해제 |
| 임베딩 처리 | `GuideEmbeddingJobHandler` | 임베딩 AI 호출 및 결과에 따른 작업 완료·실패 처리 |
| 분석 엔티티 | `VideoAnalysisBatch` | 영상 분석 배치의 구성과 소속 관리. 공통 Job과 연결 |
| | `VideoAnalysis` | 영상별 분석 상태, 생성·완료 시각, 실패 및 Guide 제외 사유 관리 |
| | `ActiveVideoAnalysis` | 영상별 활성 분석 연결 및 영상 ID 유일성 보장 |
| | `VideoAnalysisResult` | 분석 이력과 추출된 모든 Guide 연결 |
| Guide 엔티티 | `OutingGuide` | Guide 공통 데이터 및 동일성 관리 |
| | `OutingPlace` | 장소 데이터와 동작 |
| | `OutingEvent` | 행사 데이터와 동작 |
| 임베딩 작업 데이터 | `GuideEmbeddingTask` | 임베딩 대상 Guide와 공통 Job 연결. 실행 상태는 Job에서 관리 |
| 호출 컨텍스트 | `AnalysisRequestContext` | 이번 호출이 생성·공유·재사용한 분석 및 작업 ID 보관 |
| 전달 모델 | `AnalysisWorkReference` | 영상 ID·분석 ID·배치 ID·Job ID와 생성·공유·재사용 구분 |
| | `BatchAnalysisOutcome` | 배치에 속한 영상별 분석 결과 전달 |
| | `VideoAnalysisOutcome` | 영상별 상태, 유효 Guide 후보, 실패·제외 정보 전달 |
| | `GuideCandidate` | 엔티티 생성 전 Guide 데이터 |
| 값 객체 | `GuideKey` | 유형·이름·도로명주소 기반 동일성 및 고정 저장 순서 정의 |
| | `ExecutionToken` | Job ID·소유자 ID·실행 버전으로 특정 실행권 식별 |
| | `AiRequestFailure` | AI 작업의 실패 코드·사유 및 호출 오류 정보 |
| | `AnalysisFailure` | 영상별 분석 실패 코드와 사유 |
| 복합키 | `VideoAnalysisResultId` | `(videoAnalysisId, outingGuideId)` 매핑 키 |
| Enum | `AiRequestJobType` | `VIDEO_ANALYSIS`, `GUIDE_EMBEDDING` |
| | `AiRequestJobStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED` |
| | `VideoAnalysisStatus` | `IN_PROGRESS`, `SUCCESS`, `NOT_FOUND`, `FAILED` |
| | `GuideType` | `PLACE`, `EVENT` |
| | `AiRequestFailureCode` | 호출 실패·타임아웃·기한 초과·결과 저장 실패 등 구분 |
| | `AnalysisFailureCode` | AI의 영상별 실패·`INVALID_GUIDE_DATA` 등 구분 |
| Repository | `AiRequestJobRepository` | Job 저장·잠금·실행 대상 조회·조건부 갱신 |
| | `VideoAnalysisBatchRepository` | 영상 배치 구성 저장 및 조회 |
| | `VideoAnalysisRepository` | 분석 이력 저장 및 영상별 최신 이력 일괄 조회 |
| | `ActiveVideoAnalysisRepository` | 활성 작업 등록·조회·조건부 해제 |
| | `VideoAnalysisResultRepository` | 분석-Guide 매핑 저장 및 지정 분석 이력의 Guide 조회 |
| | `OutingGuideRepository` | 동일 키 조회 및 기존 필드를 보존하는 원자적 생성·충돌 처리 |
| | `GuideEmbeddingTaskRepository` | Job별 임베딩 대상 저장 및 조회 |
| 외부 연동 | `AiServerService` | 영상 분석·임베딩 HTTP 호출. 자체 재시도는 수행하지 않음 |
| 외부 DTO | `AnalyzeVideoResponseV2` | 영상별 상태와 여러 Guide를 표현하는 AI 응답 계약 |
| 팩토리 | `OutingGuideFactory` | 유효한 후보를 Place/Event 엔티티로 생성 |
| 설정 | `AiRequestJobProperties` | 유형별 병렬도·실행 수명·호출 타임아웃·재시도 및 공통 소유권 설정 |
| | `YoutubeAnalysisProperties` | 영상 배치 크기·저장 교착상태 재시도 등 분석 전용 설정 |
| | `AiRequestJobConfig` | 실행기·스케줄러·Handler 등록 및 Bean 구성 |

### 핵심 데이터 관계

```text
AiRequestJob
 ├─ VIDEO_ANALYSIS ── VideoAnalysisBatch
 │                    └─ VideoAnalysis [여러 개]
 │                        └─ VideoAnalysisResult
 │                            └─ OutingGuide
 │
 └─ GUIDE_EMBEDDING ── GuideEmbeddingTask
                       └─ OutingGuide

ActiveVideoAnalysis
 └─ 영상 ID → 현재 활성 VideoAnalysis
```

`VideoAnalysisBatch`에는 별도 실행 상태를 두지 않고 `AiRequestJob`의 상태를 사용해. `GuideEmbeddingTask` 역시 대상 정보만 보관하므로 기존 `EmbeddingOutbox`의 상태 관리가 중복되지 않아.

### 실행 흐름과 트랜잭션 경계

| 구간 | 담당 | 트랜잭션 범위 |
|---|---|---|
| 신규 분석 등록 | `VideoAnalysisWorkService` | Job·배치·분석 이력·활성 등록을 함께 생성 |
| 실행권·호출 예산 관리 | `AiRequestJobService` | 실행권 확보, heartbeat, 호출 횟수 기록 등을 각각 짧게 처리 |
| AI 호출 | 유형별 `JobHandler` | DB 트랜잭션 밖에서 호출 |
| 영상 분석 결과 저장 | `VideoAnalysisResultService` | Job 잠금 및 실행권·기한 검증 후 Guide·매핑·분석 완료·활성 등록 해제·임베딩 Job 생성·현재 Job 완료를 함께 처리 |
| 영상 분석 최종 실패 | `AiRequestJobService` + 분석 Handler | Job 실패·미완료 분석 실패·활성 등록 해제를 함께 처리 |
| 임베딩 완료·최종 실패 | 임베딩 Handler + `AiRequestJobService` | 실행권 검증 후 상태 반영 |

최종 실패는 공통 서비스가 트랜잭션을 시작하고 유형별 Handler의 업무 종료 처리를 같은 트랜잭션에 참여시키는 구조야. 성공 결과 저장과 실패 처리는 **같은 Job 잠금**으로 직렬화해.

임베딩 Job은 신규 Guide 저장과 함께 생성되므로 **Transactional Outbox 역할**을 수행해. 외부 임베딩의 중복 실행 안전성은 Guide ID 기반 upsert 또는 멱등 키로 별도 보장해야 해.

`AiRequestAttempt`는 초기 필수 클래스에서 제외했어. 우선 Job에 누적 호출 횟수와 마지막 실패를 보관하고, 호출별 상세 이력을 영속화해야 할 때 추가하면 돼.