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
