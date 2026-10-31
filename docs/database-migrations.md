# MySQL·Flyway 검증과 명시적 baseline

#223의 범위는 검증 기반이다. 운영 DB 접속·정정·baseline·migration은 이 작업에서 수행하지 않는다. Guide 중복 정리와 이름·주소의 전역 NOT NULL/유일성 적용은 개발 DB에서 V8로 수행하고, 운영 적용은 #309에서 한다.

## 기본 실행과 opt-in 프로필

기본 Flyway는 꺼져 있다. 새 baseline을 추가했다는 이유만으로 기존 애플리케이션이 DB를 변경하지 않게 한다. `baseline-on-migrate: false`, `clean-disabled: true`를 사용한다. 기존 local/test 프로필의 Hibernate 생성 동작은 유지하며 이 프로필을 운영 DB에 사용하지 않는다.

`migration` 프로필은 Flyway를 켜고 Hibernate를 `validate`, Spring SQL 초기화를 `never`로 설정한다. 명시적인 `DB_HOST`, `DB_NAME`을 요구하며 연결은 utf8mb4/utf8mb4_0900_bin을 사용한다. local/test 프로필과 조합하지 않는다. 환경 변수 등 상위 우선순위 설정으로 `ddl-auto`나 Flyway 안전 설정을 덮어쓰지 않았는지 배포 설정도 확인한다.

이 프로필의 MySqlMigrationPreflight는 baseline/migrate 전에 다음을 검사한다.

- MySQL 8.0.17 이상이며 MariaDB가 아니다.
- client/connection이 utf8mb4다. results는 utf8mb4 또는 서버 측 변환을 하지 않는 NULL이다.
- utf8mb4_0900_bin이 존재하고 NO PAD다.

버전·문자 집합·collation 미지원이면 실패한다. 이 검사는 DB의 모든 기존 컬럼이나 참조가 옳다는 보장은 아니다. 아래의 실제 스키마 비교가 별도로 필요하다.

## baseline의 출처와 한계

`V1__current_schema.sql`은 현재 JPA 엔티티와 JDBC OAuth authorized-client 저장소의 명시적 DDL snapshot이다. 현재 테이블·FK·enum·JSON·ID 할당용 `_seq` 테이블과 초기값을 포함한다. Guide의 기존 nullable 필드와 비유일 키는 유지한다. 미래 Job 테이블이나 Guide 동일성 제약을 미리 추가하지 않는다.

빈 DB는 Flyway migrate로 V1부터 만든다. 기존 DB에는 V1을 실행하거나 자동으로 baseline하지 않는다. 실제 운영 스키마와 동일하다고 가정할 수 없으므로, 비교·정정·검증 뒤에만 명시적인 version 1 baseline을 기록한다. 적용한 V1을 수정하지 않고 이후 변경은 새 버전으로 추가한다.

## 실제 MySQL 통합 테스트

Docker를 쓸 수 있는 로컬 환경의 예시다. 기존 DB나 공유 개발 DB 대신 전용 서버를 사용한다.

```bash
docker run --rm --name keepgo-mysql-tests \
  -p 127.0.0.1:33306:3306 \
  -e MYSQL_ROOT_HOST=% \
  -e MYSQL_ROOT_PASSWORD=integration-only \
  mysql:8.4.8
```

다른 터미널에서 실행한다.

```bash
export MYSQL_TEST_USER=root
export MYSQL_TEST_PASSWORD=integration-only
bash gradlew mysqlIntegrationTest \
  -PmysqlTestServerUrl=jdbc:mysql://127.0.0.1:33306/
```

CI와 동일하게 `MYSQL_TEST_SERVER_URL` 환경 변수를 사용해도 된다. URL은 스키마·쿼리 없이 `jdbc:mysql://host:port/` 형식이어야 한다. 테스트는 UUID가 붙은 `keepgo_it_` 스키마만 생성·삭제하며 서버에는 해당 권한이 필요하다. 일반 애플리케이션 DB 변수는 사용하지 않는다. 서버 설정이 없으면 skip하지 않고 실패한다. 시스템에 설치한 MySQL이나 임시 배포판도 같은 전용 URL로 실행할 수 있다.

각 실행은 실제 DB를 다시 검사한다. `check`도 이 작업을 포함하므로 `build`/`check`를 실행할 때도 전용 서버 설정이 필요하다. 일반 `test` 작업과 테스트 보고서는 분리된다.

검증 내용:

1. 빈 DB를 Flyway로 만들고 모든 현재 엔티티를 Hibernate `validate`로 확인한다. Hibernate로 테스트 스키마를 생성하지 않는다. 한국어·보조 평면 문자 왕복도 확인한다.
2. Flyway 이력이 없는 기존 스키마 fixture와 알려진 행·매핑·삭제 이력·시퀀스 값에 대해 자동 migrate가 거부되는 것을 확인한다. 명시적 baseline 뒤 테스트 전용 V2가 nullable 컬럼을 추가하며 기존 데이터를 보존하는지 확인한다.
3. latin1 연결은 migration 전에 거부되는지 확인한다.
4. 실제 Spring Boot 설정으로 기본 실행은 DB를 변경하지 않고 migration 프로필에서만 Flyway가 실행되며 안전 설정과 validate가 적용되는지 확인한다.

테스트 V2와 legacy 데이터는 mysqlIntegrationTest 리소스에만 있다. 애플리케이션 배포물에 포함되지 않는다. fixture의 스키마는 고정된 V1 snapshot을 SQL로 직접 로드해 Flyway history가 없는 상태를 재현한다. 이것이 실제 운영 스키마와 일치한다는 뜻은 아니다.

## CI 필수 실행

PR과 main/develop push에서 `MySQL migrations` 작업을 실행한다. MySQL 8.4.8 서비스에 Flyway 통합 검사를 수행하고 실패 시 보고서를 보관한다. 이미지 빌드·ECR 발행 작업은 이 작업을 `needs`로 요구한다. PR은 이미지 발행을 수행하지 않는다.

GitHub의 브랜치 보호/ruleset에서도 `MySQL migrations`를 필수 검사로 선택해야 병합 자체를 차단할 수 있다. 저장소 규칙 변경은 이 코드 작업에 포함하지 않는다. 기존 전체 애플리케이션/인증 테스트 환경 준비와는 별개다.

## 가이드 중복 이관 (V8, 개발 DB)

`V8__outing_guide_unique_key.sql`은 개발 DB 범위의 이관이며 운영 DB에는 #309에서 적용한다.

1. `scripts/guide-dedup-preflight.sql`을 읽기 전용 계정으로 실행해 중복 그룹·참조 수·이상 데이터를 이슈에 기록한다.
2. 실행 전 DB를 백업하고 migration이 끝날 때까지 다른 서버의 쓰기를 멈춘다. MySQL DDL은 암묵적으로 커밋하므로 V8은 하나의 트랜잭션으로 되돌려지지 않는다.
3. null·공백·길이 초과 데이터가 하나라도 있으면 migration이 `chk_outing_guide_dedup_guard` 위반으로 중단되고 `outing_guide_dedup_invalid`에 대상 ID와 참조 수가 남는다. 자동 보정하지 않는다. 이 중단은 이관 대상 데이터를 바꾸기 전이므로, 데이터를 고친 뒤 실패한 Flyway 이력을 `flyway repair`로 정리하고 다시 실행한다.
4. 가드 이후 단계(`outing_guide_dedup_map` 생성부터 제약 추가까지)에서 실패하면 그대로 다시 실행하지 않는다. 일부 참조가 이미 바뀌었을 수 있으므로 2의 백업으로 복원한 뒤 원인을 고치고 다시 실행한다.
5. 이관 뒤 대표가 아닌 ID와 이미지는 `outing_guide_dedup_map`에 남는다. `scripts/guide-dedup-export.sql`로 내보내 AI 벡터 삭제에 사용한다. 이미지 행은 삭제하지 않는다.

주의할 결과:

- 대표가 아닌 가이드를 삭제하면 `outing_guide_embedding_outbox`의 해당 행(PENDING·FAILED 포함)도 `on delete cascade`로 함께 삭제된다. 대표 가이드의 임베딩 작업은 그대로 남는다.
- 대표 ID로 바꾼 뒤 같은 회원의 활성 위시리스트가 겹치면, 이관 전부터 있던 중복이라도 최소 ID만 남기고 나머지에 `deleted_at`을 기록한다.
- `type`·`name`·`address` 컬럼의 collation은 모두 `utf8mb4_0900_bin`으로 바뀐다.

## 기존 DB의 스키마 비교와 baseline 절차

다음은 #234·#237에서 검증할 운영 절차이며 자동 실행 스크립트가 아니다.

1. 대상 DB·버전·환경을 명시하고 쓰기 차단·백업·복원 리허설을 준비한다. 개발/운영 DB 이름을 추측해서 접속하지 않는다.
2. `scripts/mysql-schema-preflight.sql`을 read-only 계정으로 실행해 버전·문자 집합·collation·컬럼·인덱스·FK 증거를 보관한다. `SHOW CREATE TABLE` 또는 `mysqldump --no-data`도 함께 확보한다.
3. 별도 빈 검증 DB를 V1으로 만든 결과와 실제 스키마를 비교한다. 테이블/컬럼·타입·nullable·기본값·enum·JSON·FK/유일성·문자열 비교를 확인한다. Hibernate `validate`는 모든 인덱스·collation을 비교하지 않으므로 그것만으로 동일하다고 판단하지 않는다.
4. `_seq` 이름과 현재 할당값도 비교한다. 기존 행의 최대 ID와 충돌하지 않게 기존 할당 상태를 검증하고 보존한다. V1의 초기값 1로 기존 시퀀스를 재설정하지 않는다.
5. 차이가 있으면 자동 baseline/repair로 감추지 않는다. 검증한 정정 계획과 정합성 증거를 마련하고 미해결 차이가 있으면 중지한다.
6. 실제 스키마가 version 1 상태임을 확인한 뒤, 같은 Flyway 버전과 전용 migration 계정으로 명시적 `baselineVersion=1` baseline을 수행한다. baseline은 스키마 동일성을 검증하는 명령이 아니라 이력 표시라는 점에 유의한다. baseline-on-migrate는 계속 false다.
7. 격리된 복제 DB에서 후속 버전 migrate·데이터 보존·JPA validate·DB/벡터 정합성·복원을 먼저 검증한다. 실제 적용은 승인된 전환 절차에서 수행한다.
8. 전환 후에는 Flyway migrate → Hibernate validate 순서를 사용한다. Hibernate create/update와 중복 SQL 초기화를 사용하지 않는다. 비호환 구버전 바이너리만 재배포하는 롤백은 하지 않는다.

외부 AI 호환성, Guide 데이터/벡터 이관, 실제 운영값 측정 및 배포/복원 조건은 부모 명세 #222와 후속 #234·#237의 완료 조건을 그대로 따른다.
