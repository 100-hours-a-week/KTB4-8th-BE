INSERT INTO member (id, nickname) VALUES (700, '기존 회원 🌸');
UPDATE member_seq SET next_val = 1001;
INSERT INTO outing_guide (id, type, name, address)
VALUES (900, 'place', '원문 Cafe ', NULL);
INSERT INTO video_analysis (id, video_id, status, create_datetime)
VALUES (800, 'legacy-video', 'SUCCESS', '2026-01-01 09:00:00');
INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (900, 800);
INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at, deleted_at)
VALUES (950, 700, 900, 'PLACE', '2026-01-02 09:00:00', '2026-01-03 09:00:00');
