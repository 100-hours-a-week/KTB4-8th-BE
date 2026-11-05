# YouTube 동기화 안정화 전 성능 기준선

- 측정일: 2026-10-30
- 대상 코드: `c903cc5` (`feat(youtube)!: SSE 동기화로 회원당 단일 실행·재연결·진척 전달 (#293)`)
- 목적: [#258](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/258) — 이후 안정화·성능 개선 단계가 같은 조건과 지표로 비교할 수 있는 "안정화 전" 기준값을 남긴다.
- 범위: 아직 "배치 단위 커밋·실패 격리·쓰기 차단"([#259](https://github.com/100-hours-a-week/KTB4-8th-BE/issues/259))이 병합되기 전의 동기화 경로를 측정했다.

## 고정 조건

| 항목 | 값 |
| --- | --- |
| 애플리케이션 인스턴스 | 1대 |
| MySQL | `mysql:8.4.8` (Docker), `utf8mb4_0900_bin` |
| Java | Temurin 25.0.4 |
| k6 | v2.3.0 (기본 HTTP 클라이언트) |
| 회원 수 | 40명 (측정마다 DB 초기화) |
| 회원당 좋아요 영상 | 30개, 1페이지(페이지 크기 50) |
| 영상 조건 | 업로드됨·공개·1분 |
| YouTube 스텁 지연 | 페이지 조회당 200ms |
| AI 분석 스텁 지연 | 배치당 1,000ms (영상 10개 = 1배치) |
| AI 임베딩 스텁 지연 | 호출당 1,000ms |
| 동기화 배치 크기 | 10개 |
| `keepgo.youtube-sync.max-concurrent-syncs` | 20 |
| `keepgo.youtube-sync.poll-interval-ms` | 1,000ms |
| `keepgo.youtube-sync.fetch-weight` | 10 |
| `keepgo.youtube-sync.recent-result-window` | 1s (측정용. 재동기화 시 최근 결과에 붙지 않게 하기 위함) |
| HikariCP 최대 풀 | 50 |

HikariCP 기본값 10으로 동시 5명을 실행하면 `HikariPool-1 - Connection is not available, request timed out after 30000ms`로 동기화가 실패한다. 이번 기준선은 시나리오를 끝까지 관찰하기 위해 풀을 50으로 고정했다. 기본 풀에서의 동시성 한계는 아래 "관찰"에 따로 적었다.

## 시나리오

| 파일 | 시나리오 | 내용 |
| --- | --- | --- |
| `perf/k6/first-sync.js` | 첫 동기화 | 회원 1명이 처음 보는 영상 30개를 동기화한다. 신규 가이드 30개와 임베딩 1회가 발생한다. |
| `perf/k6/resync.js` | 재동기화 | 회원 1명이 같은 영상 30개를 한 번 동기화한 뒤, 최근 결과 창이 지난 다음 같은 동기화를 다시 실행한다. 기존 가이드를 재사용하므로 임베딩은 발생하지 않는다. 현재 코드는 분석 이력을 재사용하지 않으므로 AI 분석은 다시 호출한다. |
| `perf/k6/concurrent.js` | 동시 N명 | 서로 다른 회원 5명이 각자 처음 보는 영상 30개를 동시에 동기화한다. |

## 측정 방법

- k6는 `POST /api/v3/member/youtube-sync`를 호출하고 SSE 스트림이 닫힐 때까지 응답을 읽는다.
- 주 지표인 `accepted → success`는 k6 기본 클라이언트가 SSE를 버퍼링해 이벤트 수신 시각을 따로 잡을 수 없으므로, 서버 DB의 `youtube_sync.created_at → finished_at`으로 계산했다. `accepted`는 동기화 행 생성 직후, `success`는 종료 커밋 직후 전송되므로 이벤트 수신 시각과 사실상 같은 구간이다.
- AI 분석·임베딩 호출 수는 AI HTTP 스텁의 `GET /__admin/stats`에서 읽었다.
- DB 커넥션 점유는 관리 포트의 Prometheus `hikaricp_connections_active`를 500ms 간격으로 샘플링한 최댓값이다.
- 처리량은 동기화가 모두 끝난 구간(`min(created_at) → max(finished_at)`)으로 계산했다.
- 오류율은 k6 `http_req_failed`와 동기화 실패 카운터로 확인했다.

## 결과

| 시나리오 | 동기화 수 | p50 (ms) | p95 (ms) | 최대 (ms) | AI 분석 호출/동기화 | 임베딩 호출/동기화 | DB active 최대 | 처리량 | 오류 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 첫 동기화 | 1 | 3,160 | 3,160 | 3,160 | 3 | 1 | 1 | 0.32 동기화/s | 0% |
| 재동기화 | 1 | 1,536 | 1,536 | 1,536 | 3 | 0 | 1 | 0.65 동기화/s | 0% |
| 동시 5명 | 5 | 3,450 | 3,535 | 3,545 | 3 | 1 | 7 | 1.41 동기화/s | 0% |

원본 요약은 [`results/2026-10-30/`](results/2026-10-30/)에 있다.

### 관찰

- 첫 동기화는 분석 3회(3배치)와 임베딩 1회가 직렬로 더해져 재동기화보다 약 2배 걸렸다.
- 재동기화도 AI 분석을 3회 다시 호출한다. 분석 이력 재사용은 이 기준선의 범위가 아니며, 이후 성능 단계에서 다룬다.
- 동시 5명에서는 동기화별 분석 배치가 병렬로 실행되면서 DB 커넥션 7개까지 동시에 점유했다.
- HikariCP 기본 풀(10)에서는 동시 5명만으로도 커넥션 대기 30초 후 동기화가 실패했다. 동시성 확대 전에 커넥션 점유와 트랜잭션 경계를 먼저 다뤄야 한다는 근거다.

## 재실행 방법

### 1. MySQL

```bash
docker run -d --name keepgo-perf-mysql \
  -e MYSQL_ROOT_PASSWORD=perf-only \
  -p 13306:3306 mysql:8.4.8
```

### 2. AI HTTP 스텁

```bash
./gradlew aiStub -PaiStubDelayMs=1000
```

### 3. 애플리케이션 + 측정용 회원/토큰

```bash
export KAKAO_REST_API_KEY=perf
export DB_HOST=127.0.0.1
export DB_PORT=13306
export DB_NAME=keepgo_perf
export DB_USERNAME=root
export DB_PASSWORD=perf-only
export PERF_RESET=true
export PERF_MEMBERS=40
export PERF_VIDEO_COUNT=30
export PERF_YOUTUBE_DELAY_MS=200
export PERF_MAX_CONCURRENT_SYNCS=20
export PERF_DB_POOL_SIZE=50
export AI_SERVER_API_ADDRESS=http://127.0.0.1:18080
./gradlew youtubePerfHarness
```

`PERF_HARNESS_READY`가 출력되면 `build/perf/tokens.json`에 회원별 Access Token이 생성된 것이다.

### 4. k6

각 시나리오는 DB를 초기화한 뒤 실행한다. 하네스를 재시작하면 `PERF_RESET=true`에 의해 DB가 다시 만들어진다.

```bash
k6 run perf/k6/first-sync.js

# 재동기화 전 하네스 재시작
k6 run perf/k6/resync.js

# 동시 5명 전 하네스 재시작
VUS=5 k6 run perf/k6/concurrent.js
```

결과는 `build/perf/<scenario>-summary.json`에 저장된다.

### 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `PERF_MEMBERS` | `120` | 생성할 측정용 회원 수 |
| `PERF_VIDEO_COUNT` | `30` | 회원당 영상 수 |
| `PERF_VIDEO_PAGE_SIZE` | `50` | YouTube 페이지 크기 |
| `PERF_YOUTUBE_DELAY_MS` | `200` | YouTube 스텁 페이지 지연 |
| `PERF_DB_POOL_SIZE` | `50` | HikariCP 최대 풀 |
| `PERF_MAX_CONCURRENT_SYNCS` | `20` | 인스턴스 동시 동기화 상한 |
| `PERF_RECENT_RESULT_WINDOW` | `1s` | 최근 결과 재사용 창 |
| `MEMBER_OFFSET` | `0` | k6가 사용할 토큰 목록 시작 위치 |
| `VUS` | 시나리오별 | 동시 사용자 수 |
| `SCRAPE_DURATION` | 시나리오별 | DB 커넥션 샘플링 지속 시간 |

## 한계

- 이 값은 안정화 전 코드의 기준선이며 목표값이 아니다.
- SSE `accepted` 이벤트 수신 시각을 클라이언트에서 직접 재지 않고 DB 행 시각으로 근사했다. 이후 측정에서도 같은 방식으로 비교해야 한다.
- AI·YouTube 스텁 지연은 고정값이며 실제 외부 서비스 지연 분포를 반영하지 않는다.
- 동시 5명 결과는 HikariCP 풀을 50으로 늘린 조건이다. 기본 풀 10에서는 동시 5명부터 커넥션 고갈이 발생한다.
