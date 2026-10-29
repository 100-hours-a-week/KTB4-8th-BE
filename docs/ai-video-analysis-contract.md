# AI 영상 분석 HTTP 계약

관련 작업: #224. 기준 자료는 [AI API 명세의 영상 분석 행](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=526394991#gid=526394991)이며, 2026-10-28에 확인했다.

## Wire 형식과 내부 결과

`POST /v1/analyze-video`, 요청·응답은 JSON이다. 전용 `VideoAnalysisJobClient`는 `keepgo.clients.ai.address`를 서버 주소로 사용한다. 기존 채팅·추천 및 분석 클라이언트와 별도의 HTTP 클라이언트를 사용하며, 기존 분석 진입점의 호출 경로는 이 작업에서 전환하지 않는다.

```json
{"video_urls":["https://youtube.com/shorts/aaaa"]}
```

성공 응답은 `message: "analyze_success"`와 `data.results` 배열이다. 각 행은 `video_url`, `status`, `error`와 후보 필드를 갖는다. 내부 입력 영상 ID는 요청 때 만든 정확한 URL로 역매핑하며 URL을 임의로 정규화하지 않는다.

| Wire 필드 | 내부 표현 |
|---|---|
| `video_url` | `Result.videoId` |
| `status: success / not_found / failed` | `SUCCESS / NOT_FOUND / FAILED` |
| `error: null / 실패 문자열` | `null / Failure.code` (별도 메시지는 제공되지 않음) |
| `place_type: 장소 / 이벤트` | `Guide.type: PLACE / EVENT` |
| `place_name`, `address`, `lat`, `lng` | 이름·주소·좌표, 원문 값 보존 |
| `summary`, `location_detail`, `category` | 설명·상세 위치·분류 |
| `business_hours` | 시간대·영업시간·예외일·추정 여부 |
| `event_start_date`, `event_end_date` | 날짜, 미상 시 null |
| `event_period_unresolved`, `confidence` | 기간 미확정 여부·신뢰도 |

명세 예시는 같은 `video_url`의 성공 행을 여러 번 반환한다. 따라서 Wire에서 성공 행의 URL 반복은 복수 Guide를 뜻하며, 내부에서 영상별 한 `Result`와 Guide 목록으로 묶는다. 같은 영상의 중복 미발견/실패 행이나 서로 충돌하는 상태는 허용하지 않는다. 후보 중복 제거와 저장 가능성 검증은 후속 업무 계층의 책임이다.

이는 #224의 초기 설명에 있는 영상별 `guides` 배열·대문자 유형을 **내부 결과**로 제공하는 어댑터다. 실제 외부 계약은 공유 명세의 `/v1` 및 평탄한 행 구조를 따른다. `/v2` 경로 또는 구형 응답으로의 fallback을 추가하지 않는다. 명세만으로 서버 구현의 배포 버전을 확인할 수는 없다.

## 응답 검증과 실패 전달

- 모든 요청 영상은 응답에 있어야 하며, 미요청 URL·ID 누락·알 수 없는 상태/유형·필수 구조 위반·JSON 해석 실패는 배치 전체의 `INVALID_AI_RESPONSE`다.
- 성공 행은 명시적 유형을 가진 후보 한 개를 나타낸다. 해당 영상의 성공 행들을 묶으므로 내부 SUCCESS는 Guide 1개 이상이다. NOT_FOUND/FAILED는 Guide가 없다.
- FAILED는 비어 있지 않은 실패 문자열을 요구한다. SUCCESS/NOT_FOUND의 error는 null이다. 실패·미발견 행의 후보 값은 null이어야 한다.
- 후보 이름·주소·좌표·기간 등의 누락/null은 자동 보정하거나 저장 가능하다고 간주하지 않는다. 계약상 허용된 누락은 후보별 검증에서 판단하도록 보존한다. 날짜 등 제공된 값의 타입/형식 자체가 잘못되면 구조 오류다. 이름·주소·설명·시간대 같은 문자열 필드에 숫자나 불리언이 오면 문자열로 바꾸지 않고 거부한다.
- HTTP 200 이외의 상태는 `HTTP_ERROR`로 전달한다. JSON이 아닌 오류 본문도 HTTP status와 원문 `Retry-After`를 가리지 않는다. Retry-After의 초·HTTP 날짜 해석과 재시도 결정은 Job 계층에서 수행한다.
- 전송 장애는 `TRANSPORT_ERROR`, 시간 초과는 `TIMEOUT`, 호출 스레드 중단은 `INTERRUPTED`로 전달하며 원인을 보존한다. 중단 플래그도 보존한다.

## 호출 시간과 수명

연결 제한은 5초, 분석 호출의 전체 응답 수신 제한은 130초다. 실제 호출 한도는 진입 시 받은 남은 Job 시간과 130초 중 작은 값이며, 직렬화·요청 준비에 쓴 시간도 차감한다. 본문 수신이 지연되어도 한도를 적용하고, 기한이 없으면 호출하지 않는다. 전용 Apache HttpClient의 `disableAutomaticRetries()`로 자동 복구·재실행을 끄고 redirect·구계약 fallback도 수행하지 않는다. 전역 JDK 시스템 속성은 변경하지 않는다. Apache 의존성 추가로 전송 방식이 자동 변경되지 않도록 기존 Google·지오코딩 RestClient에는 JDK 요청 팩터리를 명시한다. Spring 종료 시 전용 HTTP 클라이언트도 종료한다.

## 재현과 실제 서버 호환성 확인

로컬 계약 테스트:

```bash
bash gradlew test --tests '*VideoAnalysisJobClientTest'
```

`spreadsheet-analysis-response.json`은 공유 명세의 응답 예시를 보존하되 JSON으로 실행할 수 있도록 스마트 따옴표와 줄 구분 문자를 정리한 fixture다. `mixed-analysis-response.json`은 후보 누락과 날짜 전달을 추가 검증한다. 로컬 HTTP 서버로 요청 형식·복수 행·오류·전송 실패·헤더/본문 지연·중단을 검증한다. 날짜 배열의 거부와 `Retry-After: 0`인 503 응답에서도 요청을 한 번만 보내는 회귀 사례를 포함한다.

실제 AI 서버 확인은 명시적으로 URL과 영상 ID를 지정한다. 서버 주소나 인증 정보를 저장소에 커밋하지 않는다.

```bash
export AI_ANALYSIS_CONTRACT_URL='https://<AI 서버>/v1/analyze-video'
export AI_ANALYSIS_CONTRACT_VIDEO_IDS='aaaa,bbbb,cccc,dddd'
bash gradlew test --tests '*VideoAnalysisJobClientTest.verifiesActualServerContractWhenExplicitlyConfigured'
```

이 검사는 기본 테스트에서 생략된다. 실행 대상 서버와 입력 영상을 사용한 실제 응답을 새 계약으로 해석할 수 있어야 통과한다. 해당 테스트의 성공만으로 임베딩 upsert나 저장 완료 계약까지 검증되지는 않는다.

현재 증거는 명세와 로컬 HTTP 대역 검증이다. 실제 AI 서버의 다중 가이드·명시적 유형 지원 및 배포 여부는 #252에서 사용자 확인으로 기록했으며, 실제 서버 계약 테스트 결과는 #253 등 후속 검증에서 남긴다.

## 확인 결과 (#252, 2026-10-29)

기준 자료의 최신 `AI(v3)` 탭과 사용자 확인을 함께 반영했다. `AI(v3)` 탭은 API 계약 문서이고, 배포·용량·저장 완료 여부는 사용자가 확인했다.

| 확인 항목 | 확인 결과 | 판정 |
|---|---|---|
| 새 분석 계약의 URL·필드 | `POST /v1/analyze-video`, 요청 `video_urls`, 응답 `data.results[]`. 같은 `video_url`의 성공 행 반복이 복수 Guide이고, 각 행의 `place_type`이 `장소`/`이벤트`다. | 확인 |
| 새 분석 계약의 배포 여부·일정 | 이미 배포됐다. 별도 예정 일정은 없다. | 확인 |
| `/v1/analyze-video` 최대 동시 처리 수 | 3건이다. | 확인 |
| 임베딩의 Guide ID 기준 upsert | `POST /v1/embed-places`는 같은 `place_id`로 재호출하면 덮어쓴다. | 확인 |
| 임베딩 성공 응답의 의미 | `200`, `message: embed_success`, `data.embedded_count` 응답은 실제 저장 완료를 뜻한다. | 확인 |

- 확인된 분석 wire 형식은 `VideoAnalysisJobClient`의 현재 파싱과 일치한다.
- 분석 최대 동시 처리 수 3건을 운영 계약으로 사용한다. 다중 인스턴스에서도 전역 상한이 3건을 넘지 않도록 호출 게이트웨이에서 제한한다.
- 임베딩 upsert와 실제 저장 완료가 확인됐으므로 "upsert 미지원 또는 단순 접수 응답이면 임베딩 아웃박스를 운영에 배포하지 않는다"는 차단 조건은 해소됐다. 운영 배포는 #259의 DB baseline 등 나머지 선행 조건을 따른다.
- 실제 AI 서버에 대한 실행 계약 테스트는 이 확인과 별개로 후속 작업에서 남긴다.
