-- #249 운영 DB 읽기 전용 보고서 — 중복·참조·데이터 품질.
-- SELECT만 사용한다. 개인정보 보호를 위해 이름·주소 값은 출력하지 않고
-- 그룹 키는 HEX로만 비교하며, 보고서에는 수치와 ID만 남긴다.
--
-- 비교 기준 두 가지:
--   (A) 현재 collation  — 지금 DB가 중복으로 보는 범위
--   (B) 바이트 동일     — utf8mb4_0900_bin(NO PAD) 적용 후 중복으로 보는 범위 (권위 기준)
--       MySQL의 utf8mb4_0900_bin은 코드 포인트 비교이고 UTF-8은 순서·동등성을 보존하므로
--       HEX 비교와 동등하다. NO PAD이므로 뒤 공백도 유의미하다.

SELECT '1. 중복 그룹 요약 (A: 현재 collation 기준)' AS section;
SELECT COUNT(*) AS dup_groups,
       COALESCE(SUM(g), 0) AS rows_in_dup_groups,
       COALESCE(SUM(g) - COUNT(*), 0) AS excess_rows
FROM (
  SELECT COUNT(*) AS g
  FROM outing_guide
  WHERE type IS NOT NULL AND name IS NOT NULL AND address IS NOT NULL
  GROUP BY type, name, address
  HAVING COUNT(*) > 1
) t;

SELECT '2. 중복 그룹 요약 (B: 바이트 동일 = utf8mb4_0900_bin 기준)' AS section;
SELECT COUNT(*) AS dup_groups,
       COALESCE(SUM(g), 0) AS rows_in_dup_groups,
       COALESCE(SUM(g) - COUNT(*), 0) AS excess_rows
FROM (
  SELECT COUNT(*) AS g
  FROM outing_guide
  WHERE type IS NOT NULL AND name IS NOT NULL AND address IS NOT NULL
  GROUP BY type, HEX(name), HEX(address)
  HAVING COUNT(*) > 1
) t;

SELECT '3. 중복 그룹별 상세 (B 기준): 대표 ID·행 수·참조 수' AS section;
SELECT k.t AS guide_type,
       COUNT(*) AS guide_rows,
       COUNT(*) - 1 AS excess_rows,
       MIN(g.id) AS rep_id,
       SUM(COALESCE(w.cnt, 0)) AS wish_refs,
       SUM(COALESCE(s.cnt, 0)) AS stop_refs,
       SUM(COALESCE(a.cnt, 0)) AS analysis_refs
FROM outing_guide g
JOIN (
  SELECT type AS t, HEX(name) AS hn, HEX(address) AS ha
  FROM outing_guide
  WHERE type IS NOT NULL AND name IS NOT NULL AND address IS NOT NULL
  GROUP BY type, HEX(name), HEX(address)
  HAVING COUNT(*) > 1
) k ON k.t = g.type AND k.hn = HEX(g.name) AND k.ha = HEX(g.address)
LEFT JOIN (SELECT guide_id, COUNT(*) AS cnt FROM outing_collection_private GROUP BY guide_id) w
       ON w.guide_id = g.id
LEFT JOIN (SELECT outing_guide_id, COUNT(*) AS cnt FROM outing_stop GROUP BY outing_guide_id) s
       ON s.outing_guide_id = g.id
LEFT JOIN (SELECT outing_guide_id, COUNT(*) AS cnt FROM video_analysis_result GROUP BY outing_guide_id) a
       ON a.outing_guide_id = g.id
GROUP BY k.t, k.hn, k.ha
ORDER BY guide_rows DESC, rep_id;

SELECT '4. 중복 대상이 참조되는 총계 (B 기준)' AS section;
WITH dup AS (
  SELECT g.id
  FROM outing_guide g
  WHERE g.type IS NOT NULL AND g.name IS NOT NULL AND g.address IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM outing_guide o
      WHERE o.type = g.type
        AND HEX(o.name) = HEX(g.name)
        AND HEX(o.address) = HEX(g.address)
        AND o.id <> g.id
    )
)
SELECT (SELECT COUNT(*) FROM dup) AS guides_in_dup_groups,
       (SELECT COUNT(*) FROM outing_collection_private w JOIN dup d ON d.id = w.guide_id) AS wish_rows,
       (SELECT COUNT(*) FROM outing_collection_private w JOIN dup d ON d.id = w.guide_id
         WHERE w.deleted_at IS NULL) AS wish_rows_active,
       (SELECT COUNT(*) FROM outing_stop s JOIN dup d ON d.id = s.outing_guide_id) AS stop_rows,
       (SELECT COUNT(*) FROM video_analysis_result a JOIN dup d ON d.id = a.outing_guide_id) AS analysis_rows;

SELECT '5. 품질 문제 요약: null·공백·255자 초과' AS section;
SELECT SUM(name IS NULL) AS name_null,
       SUM(name IS NOT NULL AND TRIM(name) = '') AS name_blank,
       SUM(CHAR_LENGTH(name) > 255) AS name_too_long,
       SUM(address IS NULL) AS address_null,
       SUM(address IS NOT NULL AND TRIM(address) = '') AS address_blank,
       SUM(CHAR_LENGTH(address) > 255) AS address_too_long,
       SUM(type IS NULL) AS type_null
FROM outing_guide;

SELECT '6. 품질 문제 행별 ID와 참조 수' AS section;
SELECT g.id,
       g.type AS guide_type,
       (g.name IS NULL) AS name_null,
       (g.name IS NOT NULL AND TRIM(g.name) = '') AS name_blank,
       CHAR_LENGTH(g.name) AS name_len,
       (g.address IS NULL) AS addr_null,
       (g.address IS NOT NULL AND TRIM(g.address) = '') AS addr_blank,
       CHAR_LENGTH(g.address) AS addr_len,
       (SELECT COUNT(*) FROM outing_collection_private w WHERE w.guide_id = g.id) AS wish_refs,
       (SELECT COUNT(*) FROM outing_stop s WHERE s.outing_guide_id = g.id) AS stop_refs,
       (SELECT COUNT(*) FROM video_analysis_result a WHERE a.outing_guide_id = g.id) AS analysis_refs
FROM outing_guide g
WHERE g.name IS NULL OR TRIM(g.name) = '' OR CHAR_LENGTH(g.name) > 255
   OR g.address IS NULL OR TRIM(g.address) = '' OR CHAR_LENGTH(g.address) > 255
ORDER BY g.id;

SELECT '7. 보충: 앞뒤 공백이 있는 값 (NO PAD에서 별개 키가 된다)' AS section;
SELECT SUM(HEX(name) <> HEX(TRIM(name))) AS name_edge_space,
       SUM(HEX(address) <> HEX(TRIM(address))) AS address_edge_space
FROM outing_guide
WHERE name IS NOT NULL AND TRIM(name) <> ''
  AND address IS NOT NULL AND TRIM(address) <> '';

SELECT '8. 보충: 대소문자만 달라 (A에서는 중복, B에서는 별개) 그룹 수' AS section;
SELECT COUNT(*) AS case_only_groups
FROM (
  SELECT type, LOWER(name), LOWER(address)
  FROM outing_guide
  WHERE type IS NOT NULL AND name IS NOT NULL AND address IS NOT NULL
  GROUP BY type, LOWER(name), LOWER(address)
  HAVING COUNT(DISTINCT HEX(name)) > 1 OR COUNT(DISTINCT HEX(address)) > 1
) t;
