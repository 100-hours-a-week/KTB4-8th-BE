# KTB4-8th-BE

## 데이터 모델

- [ERD — 전체 관계도와 테이블별 주요 제약](docs/erd.md)

## API 문서

- [API 명세 목차 — FE–BE·AI 기능별·버전별 계약](docs/api/README.md)
- [명세와 현재 구현의 차이](docs/api/implementation-notes.md)
- [AI 영상 분석 HTTP 계약과 운영 확인 기록](docs/ai-video-analysis-contract.md)

## 협업 규칙

- [브랜치 전략](docs/branch-strategy.md)
- [커밋 및 PR 작성 규칙](CONTRIBUTING.md)
- [PR 전략](docs/pr-strategy.md)

## 환경 변수
- 임의로 만들었습니다.
- 기본적으로 MySQL, 제 로컬 환경 기준입니다.

|환경변수|설명|기본값|
|---|---|---|
|DB_VENDOR|DB의 종류|mysql|
|DB_HOST|DB 서버의 호스트 주소|172.25.96.1|
|DB_PORT|DB 서버의 포트|3306|
|DB_NAME|DB의 이름|keepgo|
|DB_TIMEZONE|DB의 시간대|Asia/Seoul|
|DB_USERNAME|DB에 접속할 계정의 ID|keepgo|
|DB_PASSWORD|DB에 접속할 계정의 비밀번호|0000|
|DB_DRIVER|DB에 해당하는 JDBC 드라이버 이름|com.mysql.cj.jdbc.Driver|
|DB_DIALECT|DB에 해당하는 SQL 방언|MySQL|
|GOOGLE_CLIENT_ID|Google OAuth(YouTube 연동) 클라이언트 ID. 없으면 애플리케이션 기동이 실패합니다|없음(필수)|
|GOOGLE_CLIENT_SECRET|Google OAuth(YouTube 연동) 클라이언트 시크릿. 없으면 애플리케이션 기동이 실패합니다|없음(필수)|
|GOOGLE_LOGIN_CLIENT_ID|Google 로그인용 OAuth 클라이언트 ID|없음|
|GOOGLE_LOGIN_CLIENT_SECRET|Google 로그인용 OAuth 클라이언트 시크릿|없음|

로컬에서는 `.env.example`을 참고해 환경 변수를 주입합니다. 기동 시점에 값을 검증하는 대상은 현재 `GOOGLE_CLIENT_ID`와 `GOOGLE_CLIENT_SECRET`뿐입니다.

## 제품 문서

- [사용자 페르소나](docs/persona.md)
