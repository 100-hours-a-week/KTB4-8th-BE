-- 분석 이력의 완료 시각. 분석 중 기록을 전송 직전에 만들면서 create_datetime으로는 완료 순서를 알 수 없게 됐다.
-- IN_PROGRESS 행은 null이고, 성공·실패로 끝날 때 채운다.
alter table video_analysis add column completed_at datetime(6) null;
