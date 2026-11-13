# OAuth 인증 요청 저장 운영 절차

OAuth 인증 요청은 기본적으로 MySQL에 10분 동안 보관한다. 완료된 Google access token과 refresh token의 JDBC 저장은 별도이며 이 설정으로 바뀌지 않는다.

배포 전에 기존 명시적 마이그레이션 절차에 따라 V10 스키마를 적용한다. 기본 실행의 Flyway 자동 마이그레이션은 비활성 상태로 유지한다. 모든 파드는 같은 OAuth provider와 같은 MySQL 및 호환 가능한 저장 형식을 사용해야 한다.

첫 배포 전 로컬 세션에 남았거나 저장소 전환 전에 시작한 요청은 콜백 시 실패할 수 있다. 실패하면 설정된 FE 로그인 화면에서 로그인을 다시 시작한다. 전환 중 일시적인 로그인 실패가 발생할 수 있다.

설정은 `keepgo.security.oauth2.authorization-request` 아래에서 관리한다. 기본 provider는 `rdb`, `ttl`은 `10m`, `secure-cookie`는 `true`다. 로컬 HTTP 환경에서만 `secure-cookie=false`를 명시할 수 있다. `failure-url`은 FE 로그인 화면의 고정 주소로 설정한다. 이 provider는 `keepgo.ephemeral.provider`와 독립적이며 저장소 장애 시 자동 대체하지 않는다.
