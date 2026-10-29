-- #249 운영 DB 읽기 전용 보고서 — 접속·환경 증거.
-- SELECT / SHOW만 사용한다. 이 파일에 쓰기 문장을 추가하지 않는다.
-- 반드시 대상 DB를 명시해 실행한다: mysql ... -D <db> < guide-report-environment.sql

SELECT '1. 접속·계정 증거' AS section;
SELECT CURRENT_USER() AS account,
       DATABASE() AS selected_database,
       @@read_only AS server_read_only,
       @@super_read_only AS server_super_read_only;
SHOW GRANTS\G

SELECT '2. 서버 버전·vendor (8.0.17 이상, MariaDB 아님)' AS section;
SELECT VERSION() AS mysql_version,
       @@version_comment AS vendor;

SELECT '3. utf8mb4_0900_bin 지원 여부 (PAD_ATTRIBUTE=NO PAD)' AS section;
SELECT COLLATION_NAME, CHARACTER_SET_NAME, PAD_ATTRIBUTE
FROM information_schema.COLLATIONS
WHERE COLLATION_NAME = 'utf8mb4_0900_bin';

SELECT '4. DB 기본 문자 집합·collation' AS section;
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = DATABASE();

SELECT '5. outing_guide 테이블 collation' AS section;
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'outing_guide';

SELECT '6. outing_guide 유형·이름·주소 컬럼의 문자 집합·collation' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'outing_guide'
  AND COLUMN_NAME IN ('type', 'name', 'address')
ORDER BY ORDINAL_POSITION;
