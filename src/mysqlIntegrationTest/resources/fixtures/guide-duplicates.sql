-- (유형, 이름, 도로명주소)가 같은 가이드와 바이트 단위로만 다른 가이드를 함께 둔다.
INSERT INTO member (id, nickname) VALUES (700, '기존 회원 🌸');

INSERT INTO file (id, storage_type) VALUES (4001, 'LOCAL');
INSERT INTO file (id, storage_type) VALUES (4002, 'LOCAL');

INSERT INTO outing_guide (id, type, name, address, image_id)
VALUES (910, 'place', '같은 카페', '서울시 강남구 테헤란로 1', 4001);
INSERT INTO outing_guide (id, type, name, address, image_id)
VALUES (911, 'place', '같은 카페', '서울시 강남구 테헤란로 1', 4002);
INSERT INTO outing_guide (id, type, name, address)
VALUES (912, 'place', '같은 카페', '서울시 강남구 테헤란로 1');

-- 유형이 다르거나 바이트 단위로 다르면 다른 가이드다.
INSERT INTO outing_guide (id, type, name, address)
VALUES (920, 'event', '같은 카페', '서울시 강남구 테헤란로 1');
INSERT INTO outing_guide (id, type, name, address)
VALUES (930, 'place', '같은 카페 ', '서울시 강남구 테헤란로 1');
INSERT INTO outing_guide (id, type, name, address)
VALUES (931, 'place', '같은 카페', '서울시 강남구 테헤란로 1 ');

INSERT INTO outing_course (id, name, duration_hours, recommendation_score)
VALUES (5001, '중복 가이드 코스', 3, 10);
INSERT INTO outing_stop (outing_course_id, sequence, outing_guide_id, travel_minutes) VALUES (5001, 1, 911, 5);
INSERT INTO outing_stop (outing_course_id, sequence, outing_guide_id, travel_minutes) VALUES (5001, 2, 910, 6);
INSERT INTO outing_stop (outing_course_id, sequence, outing_guide_id, travel_minutes) VALUES (5001, 3, 930, 7);

INSERT INTO video_analysis (id, video_id, status, create_datetime)
VALUES (800, 'legacy-video-a', 'SUCCESS', '2026-01-01 09:00:00');
INSERT INTO video_analysis (id, video_id, status, create_datetime)
VALUES (801, 'legacy-video-b', 'SUCCESS', '2026-01-01 09:00:00');
INSERT INTO video_analysis (id, video_id, status, create_datetime)
VALUES (802, 'legacy-video-c', 'SUCCESS', '2026-01-01 09:00:00');
INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (910, 800);
INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (911, 800);
INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (911, 801);
INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (910, 802);

INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at)
VALUES (950, 700, 910, 'PLACE', '2026-01-02 09:00:00');
INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at)
VALUES (951, 700, 911, 'PLACE', '2026-01-03 09:00:00');
INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at, deleted_at)
VALUES (952, 700, 910, 'PLACE', '2026-01-02 09:00:00', '2026-01-04 09:00:00');
INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at)
VALUES (953, 700, 920, 'EVENT', '2026-01-05 09:00:00');
