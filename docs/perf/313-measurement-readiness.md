# #313 측정 재개 전 조건 점검

점검일: 2026-11-02(KST). 앱·수신기·DB를 실행하거나 초기화하지 않은 정적 점검이다. 조사 기준은 `bae96c5e51aceb2aafb39438eb737a83d427395a`이며 진행 중인 #337 작업 파일은 수정하지 않았다. 새 성능 표본·채택 판정을 만들지 않았다.

## 결론

재개 전에 실제 실행 소스와 비교 SHA 일치, 교착 수정 후 비교 범위, 실행 환경 기록을 확정해야 한다. `db_active_max=5~6`은 AI 분석 상한 3 위반의 근거가 아니다.

정본: [#276 조건](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/276#issuecomment-6545288494), [#275 절차](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/275#issuecomment-6512080875), [#313 진행 기록](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/313). 이 문서는 준비 점검으로 예비 측정 결과나 최종 성능 기록을 대체하지 않는다.

## 비교 SHA

| 역할 | 전체 SHA |
| --- | --- |
| #316 실제 부모 | `01373472f03d10177ce88837aaf2834c64873e7a` |
| #316 병합 | `9fea9baddc5f05b75b5f96a7373e09251ff4c590` |
| 교착 수정 #327 병합 | `27701777c4cc5c91b51e5ed8a1255620b5534c39` |
| 조사한 도구 기준 | `bae96c5e51aceb2aafb39438eb737a83d427395a` |

`git show -s --format='%H %P %s'`로 부모 관계를 확인했다. 원래 공식 비교는 `0137347→9fea9ba`이며 교착 수정이 없다. #327의 부모는 `4ac47675652fecf3c1996063a207f91895589961`로, 이 부모와 #327을 비교하면 교착 수정 효과만 비교하게 된다.

`git diff --stat` 기준 `9fea9ba..2770177`의 생산 코드 차이는 `SharedVideoAnalysisService`와 `ApiFilterChainConfig`다. `2770177..bae96c5`에는 추천 V1 API 종료도 있다. 최신 후보를 실행하면서 `--candidate-sha 9fea9ba`로 기록하면 실행 소스와 다르다. 이전 기록의 `d5fbd7a`는 로컬 객체 저장소에서 찾지 못해 동일성을 검증하지 않았다.

권고는 원래 부모와 교착 수정을 포함한 후보를 고정하고 전체 diff·이유를 기록하는 것이다. 이를 원래 #316 merge SHA의 공식 결과로 표기하지 않는다. #275의 merge↔parent 규칙에 대한 처리 방침을 확정한 뒤 판정에 사용한다. 알려진 정합성 실패 회차는 정상 표본으로 채택하지 않는다.

근거: [#316](https://github.com/100-hours-a-week/KTB4-8th-BE/commit/9fea9baddc5f05b75b5f96a7373e09251ff4c590), [#327](https://github.com/100-hours-a-week/KTB4-8th-BE/commit/27701777c4cc5c91b51e5ed8a1255620b5534c39).

## 조건·초기화

| 항목 | 고정 소스 점검 결과 |
| --- | --- |
| 데이터 | 40명, first 30개, resync 사전 30개→본 40개. 일반 시나리오는 회원별 영상 ID 분리, shared는 같은 30개 |
| 지연 | YouTube 페이지 50개/200ms, 분석·임베딩 각각 1000ms |
| 반복 | 기본 N=1·5·10·20, 각 버전·시나리오·N에 워밍업 2회·본 20회. 버전 순서 교대·워밍업 집계 제외 |
| 후속 완료 | active sync·아웃박스·점유 슬롯·AI in-flight 0, 최근 종료 1초 경과 및 추가 idle 관찰. FAILED 아웃박스도 남으면 통과하지 않음 |
| 재동기화 | 매회 사전 실행·후속 완료 후 40개 fixture 전환, AI 통계 초기화. 본 실행에 DB 관측 새로 시작 |
| 복원 | 작업 테이블 TRUNCATE, 회원·Flyway·슬롯·`*_seq` 유지. 앱과 Hibernate 메모리 내 ID 할당 범위 유지 |
| 주 지표 | 완성된 SSE accepted→success 프레임 수신 단조 시각 차이. DB 시각은 검증용 |

복원은 논리적 작업 데이터 초기화이며 ID·시퀀스·캐시까지 같은 snapshot은 아니다. 기존 #313에도 명시한 한계다. ID 동일성이 필수라면 별도 준비 변경이 필요하다. 앱 유지·교차 순서로 warm cache 영향을 관리하되 제거했다고 주장하지 않는다.

근거: [quiet/run/aggregate](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/perf/loop313.py#L79), [하네스 reset/state](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessApplication.java), [fixture](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessConfiguration.java), [스텁 main](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/src/test/java/eightjbbm/keepgo/integration/stub/AiServerStub.java#L332).

## DB 지표 해석

`db_active_max`는 대상 앱 하나의 `hikaricp_connections_active`를 pool 라벨 구분 없이 합산한 관측값의 최대다. 두 앱을 합산한 값은 아니다. `db_pending_max`는 DB 연결 획득 대기 스레드 수이며 AI 대기열 길이가 아니다. 하네스 풀 상한은 50이다.

AI 분석 기본 상한 3·임베딩 1은 DB 행으로 임대하는 논리 슬롯이다. 슬롯 점유와 JDBC 연결 지속 점유는 다르다. 실행권 판정·이력 조회·합류 폴링·진행 조회·저장·후속 작업도 DB를 쓰므로 active 5~6을 분석 상한 위반으로 해석하지 않는다. 어떤 경로가 그 최대값을 만들었는지는 게이지만으로 귀속할 수 없다.

monitor는 k6 시작 전에 시작해 종료 후 중단하므로 정확히 SSE 구간만 관측하지 않는다. 회원별 SSE 종료 후 `/syncs/latest` 검증도 같은 JdbcTemplate을 사용하여 관측에 포함될 수 있다. 후속 완료 대기 `quiet()`는 monitor 종료 후 수행한다. AI 호출 수는 후속 완료 후 수집하므로 지표별 시간 범위가 다르다.

250ms scrape가 상태 해상도 250ms를 보장하지 않는다. 로컬 HikariCP 7.0.2 원본 sources.jar에서 `HikariPool.getPoolStats()`는 `new PoolStats(SECONDS.toMillis(1))`을 쓰고 Micrometer gauge는 캐시된 PoolStats getter를 읽는다. 짧은 피크가 누락될 수 있어 pending=0 또는 active_max<50만으로 순간 대기·고갈이 없었다고 단정하지 않는다. 최종 빌드의 실제 Hikari 버전도 다시 확인한다.

근거: [scrape/aggregate](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/perf/loop313.py#L113), [AI 설정](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/src/main/java/eightjbbm/keepgo/util/client/ai/gateway/AiCallProperties.java), [공유 분석](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/src/main/java/eightjbbm/keepgo/interestextraction/service/youtubesync/SharedVideoAnalysisService.java). 로컬 원본은 `/home/user/.gradle/caches/modules-2/files-2.1/com.zaxxer/HikariCP/7.0.2/b7d35f0e85bbc811111077785e5fbcd148874d3f/HikariCP-7.0.2-sources.jar`의 `HikariPool.java:673`, `PoolStats.java:64`, `MicrometerMetricsTracker.java:127`이다.

## 실행 환경 기록의 공백

기동 스크립트는 풀 50·동기화 상한 20·최근 결과 창 1초·프로세스별 `-Xmx512m`·전용 두 스키마와 포트를 설정한다. compose는 MySQL 8.4.8·k6 2.3.0 태그다. 재개 시 다음을 결과 디렉터리에 기록한다.

- 양쪽 전체 HEAD·dirty diff, 공통 하네스 overlay, 빌드 설정·의존성·classpath 해시. 측정 중 소스 고정.
- Java/Python/OS/Docker/compose 실제 버전, MySQL/k6 이미지 ID·digest, CPU·메모리·Docker 자원 할당, 일시(KST).
- 양쪽 실효 설정. 하네스의 `PERF_POLL_INTERVAL_MS`, `PERF_FETCH_WEIGHT`는 기동 스크립트가 고정하지 않아 기존 환경값 영향을 확인한다.
- 실제 Hikari pool 이름·상한, AI 슬롯, Flyway 버전, 양쪽 후속 작업 완료. 다른 작업이 같은 장비·DB·포트를 쓰는 동안 실행하지 않는다.

현재 metadata는 OS·일부 조건·도구 6개 해시와 전달받은 SHA를 기록한다. 실행 바이너리와 SHA 일치, Java/Python 버전·이미지 digest·빌드 설정 해시는 자동 검증하지 않는다. 기동 스크립트는 base HEAD와 양쪽 src/main dirty 여부를 검사하지만 candidate HEAD는 고정하지 않는다. `full-unreviewed`도 반복 수 2/20만 보고 붙여 N=1·5만 수행한 결과를 전체 완료로 해석하면 안 된다.

근거: [metadata](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/perf/loop313.py#L137), [기동 검사·설정](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/perf/start313.ps1#L11), [compose](https://github.com/100-hours-a-week/KTB4-8th-BE/blob/bae96c5e51aceb2aafb39438eb737a83d427395a/perf/compose.yaml).

## 재개 순서

1. #337 완료 후 도구 SHA·해시 고정. 해당 작업 디렉터리와 측정 자원을 공유하지 않는다.
2. 비교 쌍 처리 방침·환경 기록 확정. 양쪽 소스·공통 하네스 동일 조건 빌드와 관련 CI·계약 검증 확보.
3. 최종 도구·후보의 N=10·20 first/resync/shared 정합성 재확인 및 N=1·5 smoke 기록 확인.
4. 같은 날 교차 반복. 전체 3시나리오×4N×2버전×22회=528회(워밍업 포함), 재동기화 사전 실행은 별도다. N=1·5만 수행하면 264회로 전체 판정 근거가 되지 않는다.
5. N별 재동기화 p95 감소율, 첫 동기화 p50/p95 악화율, 오류·정합성·호출 수 보고. N=1 본 표본 20개 한계 명시. 사람의 판정·기록 확정까지 #313 OPEN 유지.

이번 문서는 Git 객체·diff·고정 소스 정적 점검 결과다. 성능 실행·통합 테스트는 새로 수행하지 않았다.
