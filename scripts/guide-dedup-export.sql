-- 이관 후 대표가 아닌 가이드 ID와 이미지 목록. AI 팀이 Chroma 벡터를 삭제할 때 사용한다.
-- guide_id가 벡터 ID이고 representative_id는 남은 가이드다. 이미지는 삭제하지 않는다.
SELECT guide_id AS removed_guide_id, representative_id, image_id
FROM outing_guide_dedup_map
WHERE guide_id <> representative_id
ORDER BY guide_id;
