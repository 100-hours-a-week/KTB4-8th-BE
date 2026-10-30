-- 분석 이력에 저장을 거부한 후보와 실패 원인을 남긴다.
-- excluded_candidates는 [{ "index": 0, "reason": "NAME_MISSING" }] 형태의 JSON 배열이다.
alter table video_analysis add column failure_code varchar(64) null;
alter table video_analysis add column excluded_candidates json null;
