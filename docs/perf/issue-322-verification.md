# #322 커넥션 고갈 직접 검증

- 측정일: 2026-11-01 (Asia/Seoul)
- 기준 HEAD: `1e10f53d5e58b21a6bb49c26bc89e6c23240a0ad`
- 판정: 풀 10·동시 5명에서 주장된 30초 커넥션 획득 타임아웃은 재현되지 않았다. 운영 환경 전체의 안전성을 증명하거나 풀 증설 필요성을 부정하는 결과는 아니다.

## 환경과 방법

Windows 호스트, Java 25.0.3, 전용 MySQL 8.4.8 컨테이너, k6 2.3.0 Docker 이미지를 사용했다. 기존 DB 대신 포트 13322의 전용 컨테이너와 `triage_322`, `triage_322_limit3` DB를 만들었다. 실제 Spring 애플리케이션·Flyway·MySQL을 사용하는 기존 `youtubePerfHarness`와 `perf/k6/concurrent.js`로 V3 동기화 API를 호출했다.

풀 크기는 `PERF_DB_POOL_SIZE=10`, 회원 40명, 회원당 서로 다른 영상 30개, YouTube 스텁 지연 200ms, AI 분석·임베딩 스텁 지연 1,000ms로 고정했다. 실행 상한만 20과 3으로 나눴다. 상한 20의 두 번째 실행은 `MEMBER_OFFSET=10`으로 아직 처리하지 않은 회원·영상을 사용했다. 각 실행에서 Hikari active/pending을 약 500ms 간격으로 40초 동안 관찰했다. 최종 Prometheus에서 두 서버 모두 maximum=10, timeout_total=0을 확인했고, 두 하네스 로그에 해당 커넥션 획득 타임아웃 문구는 없었다.

두 하네스 모두 `compileJava UP-TO-DATE`로 같은 빌드 산출물을 사용했다. 측정 도중 다른 작업이 `SharedVideoAnalysisService`와 관련 테스트를 수정했으므로, 이 결과는 측정에 사용한 빌드에 대한 결과이며 이후 작업 트리의 재빌드 결과를 뜻하지 않는다. 기존 미커밋 문서 변경도 있었다.

## 결과

| 풀 / 동기화 상한 | 요청 | 성공 | 초과 거절 | DB active 최대 | DB pending 최대 | 성공 p95 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 10 / 20, 1회차 | 5 | 5 | 0 | 6 | 0 | 8,533 / 8,610ms |
| 10 / 20, 다른 회원 2회차 | 5 | 5 | 0 | 6 | 0 | 7,809 / 8,138ms |
| 10 / 3 | 5 | 3 | 2 | 2 | 0 | 6,343 / 6,407ms |

상한 3에서는 2개 요청이 약 1,094ms 만에 HTTP 200 SSE의 `youtube-sync.service-unavailable` 이벤트로 거절됐으며 accepted/success 이벤트가 없었다. 이는 `YoutubeSyncCoordinator`의 상한 검사 → `SYNC_BUSY` → SSE 거절 경로와 일치한다. k6의 `failed_syncs=2`는 이 거절도 실패로 세므로 커넥션 고갈로 해석하면 안 된다. DB에 접수된 3개 동기화는 성공했다.

측정 요약은 [results/2026-11-01/issue-322.json](results/2026-11-01/issue-322.json)에 있다. 원본 k6 출력·하네스 로그·최종 Prometheus는 로컬 `build/triage-322/` 및 `build/triage-322*-harness.log`에 있다. 인증 토큰과 임시 키는 결과 문서에 포함하지 않았다.

## 재실행

1. 전용 MySQL 8.4.8과 `./gradlew aiStub -PaiStubPort=18320 -PaiStubDelayMs=1000`을 실행한다.
2. DB 접속은 전용 DB로 지정하고 `PERF_DB_POOL_SIZE=10`, `PERF_MAX_CONCURRENT_SYNCS=20`, `PERF_MEMBERS=40`, `AI_SERVER_API_ADDRESS=http://127.0.0.1:18320`으로 `./gradlew youtubePerfHarness`를 실행한다. API/management/admin 포트와 토큰 경로는 하네스 환경 변수로 분리한다.
3. 하네스 준비 후 k6에 해당 API/management/admin/AI 주소와 `TOKENS_FILE`, `SUMMARY_OUT`을 전달하고 `VUS=5 SCRAPE_DURATION=40s k6 run perf/k6/concurrent.js`를 실행한다. Docker k6에서는 호스트 주소를 `host.docker.internal`로 지정하고 저장소를 마운트한다.
4. 같은 서버에서 `MEMBER_OFFSET=10`으로 반복한다. 기본 상한 비교는 새 전용 DB로 하네스를 기동하고 `PERF_MAX_CONCURRENT_SYNCS=3`만 바꿔 동일 시나리오를 실행한다.
5. `/actuator/prometheus`의 `hikaricp_connections_max`, `hikaricp_connections_timeout_total`과 서버 로그, SSE 결과를 함께 확인한다. k6는 성공률 threshold가 없으므로 종료 코드만으로 성공을 판정하지 않는다.

## 해석과 한계

이슈가 인용한 기준선은 안정화 전 `c903cc5`의 결과다. 현재 배치 커밋·임베딩 아웃박스 분리가 존재하고, 이번 측정에서는 해당 실패를 관찰하지 못했다. 따라서 이 근거만으로 풀을 최소 30~50으로 늘리는 수정은 정당화되지 않는다.

실제 외부 API 지연 분포, 추천과 동기화의 혼합 부하, 장시간 부하, 다중 인스턴스, 운영 DB 자원 제약은 측정하지 않았다. active/pending 최대는 샘플값이므로 짧은 피크를 놓칠 수 있다. 두 앱이 하나의 AI 스텁을 공유했고 이전 실행의 임베딩 아웃박스가 계속 처리될 수 있으므로 임베딩 호출 수는 실행 간 비교에 사용하지 않는다. 기존 성능 기준선과 OS·Java 패치 버전도 다르므로 지연값의 직접 회귀 비교에는 사용하지 않는다.

제보자에게 추가 답변을 요구하는 대신 직접 검증한 결과를 이슈에 기록한다. 현재 주장에 대한 판정은 미재현이며, 후속 조치 여부는 이 결과를 근거로 재평가한다.
