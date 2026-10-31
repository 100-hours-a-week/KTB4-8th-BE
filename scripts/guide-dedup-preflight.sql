-- 이관 전 개발 DB 읽기 전용 사전 보고서. 쓰기 문장을 포함하지 않는다.
-- 명시적으로 선택한 데이터베이스에서만 실행하고 결과를 이슈에 기록한다.
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version,
       @@version_comment AS vendor, @@character_set_database AS database_charset,
       @@collation_database AS database_collation;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outing_guide'
ORDER BY ORDINAL_POSITION;

-- (유형, 이름, 도로명주소)가 바이트 단위로 같은 그룹 수와 정리 대상 행 수.
SELECT COUNT(*) AS duplicate_group_count, SUM(row_count - 1) AS removable_row_count
FROM (
    SELECT COUNT(*) AS row_count
    FROM outing_guide
    GROUP BY CAST(type AS BINARY), CAST(name AS BINARY), CAST(address AS BINARY)
    HAVING COUNT(*) > 1
) duplicate_groups;

-- 중복 그룹에 속한 행과 대표(최소 ID) 후보, 위시리스트·코스 정류장·분석 결과 참조 수.
SELECT g.id AS guide_id, g.type, g.name, g.address, d.representative_id,
       (SELECT COUNT(*) FROM outing_collection_private w WHERE w.guide_id = g.id) AS wishlist_reference_count,
       (SELECT COUNT(*) FROM outing_stop s WHERE s.outing_guide_id = g.id) AS course_stop_reference_count,
       (SELECT COUNT(*) FROM video_analysis_result r WHERE r.outing_guide_id = g.id) AS analysis_reference_count
FROM outing_guide g
JOIN (
    SELECT CAST(type AS BINARY) AS type_key, CAST(name AS BINARY) AS name_key,
           CAST(address AS BINARY) AS address_key, MIN(id) AS representative_id
    FROM outing_guide
    GROUP BY type_key, name_key, address_key
    HAVING COUNT(*) > 1
) d ON d.type_key = CAST(g.type AS BINARY)
   AND d.name_key = CAST(g.name AS BINARY)
   AND d.address_key = CAST(g.address AS BINARY)
ORDER BY g.id;

-- null·공백·길이 초과 데이터와 참조 수. 한 행이라도 있으면 이관을 중단한다.
SELECT g.id AS guide_id, g.type, g.name, g.address,
       (SELECT COUNT(*) FROM outing_collection_private w WHERE w.guide_id = g.id) AS wishlist_reference_count,
       (SELECT COUNT(*) FROM outing_stop s WHERE s.outing_guide_id = g.id) AS course_stop_reference_count,
       (SELECT COUNT(*) FROM video_analysis_result r WHERE r.outing_guide_id = g.id) AS analysis_reference_count
FROM outing_guide g
WHERE g.name IS NULL OR TRIM(g.name) = '' OR CHAR_LENGTH(g.name) > 255
   OR g.address IS NULL OR TRIM(g.address) = '' OR CHAR_LENGTH(g.address) > 255
ORDER BY g.id;

-- 대표가 아닌 가이드의 이미지. 대표를 정한 뒤에는 guide-dedup-export.sql의 map 조회를 쓴다.
SELECT g.id AS guide_id, g.image_id
FROM outing_guide g
JOIN (
    SELECT CAST(type AS BINARY) AS type_key, CAST(name AS BINARY) AS name_key,
           CAST(address AS BINARY) AS address_key, MIN(id) AS representative_id
    FROM outing_guide
    GROUP BY type_key, name_key, address_key
) d ON d.type_key = CAST(g.type AS BINARY)
   AND d.name_key = CAST(g.name AS BINARY)
   AND d.address_key = CAST(g.address AS BINARY)
WHERE g.image_id IS NOT NULL AND g.id <> d.representative_id
ORDER BY g.id;
