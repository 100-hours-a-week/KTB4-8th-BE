# OAuth 장애 관측

OAuth 인증 실패 중 만료, 재전송, 브라우저 검증 실패와 동의 취소는 WARN으로 남긴다. 저장소 장애와 예상하지 못한 오류는 ERROR 로그와 제한된 분류, 단계, provider를 담은 Sentry 이벤트로 남긴다. 이벤트에는 예외 원문이나 인증 요청을 첨부하지 않는다.

Sentry DSN은 기존 `sentry.dsn` 설정 또는 `SENTRY_DSN` 환경 변수로 제공한다. DSN이 없으면 수동 이벤트를 전송하지 않으며 서버 로그는 유지한다. SDK 전송 실패도 로그인 실패 리다이렉트를 막지 않는다.

배포 후 테스트용 인증 실패와 저장소 장애를 구분하여 로그와 Sentry에서 확인한다. 예상 실패는 이벤트가 없고 서버 장애는 한 건이어야 한다. 이벤트에 쿼리, 헤더, 쿠키, breadcrumb, 토큰과 예외 원문이 없는지 확인한다. 운영 DSN과 실제 수집 상태는 이 변경에서 검증하지 않았다. 배포 환경이 별도 Java agent나 로그 수집기를 주입한다면 동일 장애의 중복 보고와 민감 정보 자동 첨부를 별도로 확인한다. 저장소에 정의된 Docker 이미지는 agent를 포함하지 않으며 `bootRun`만 agent를 지정한다.

테스트에서는 Sentry Spring SDK와 Logback의 자동 이벤트 수집을 켜고 테스트 transport로 OAuth 장애 이벤트를 확인했다. 별도로 Sentry OpenTelemetry agent 8.58.0을 `SENTRY_AUTO_INIT=false`로 실행한 HTTP 테스트도 통과했다. 이 설정은 agent 자체의 Sentry 자동 초기화를 끄므로 agent 자동 이벤트 수집 전체의 중복 방지를 입증하지 않는다. 테스트 환경에는 OTLP 수집기가 없어 agent의 OTLP 로그·trace·metric 전송 내용도 검증하지 못했다. 실제 배포에 사용되는 agent 초기화 설정과 OTLP 수집 경로의 민감 정보 제거 및 중복 여부를 배포 후 별도로 확인해야 한다.
