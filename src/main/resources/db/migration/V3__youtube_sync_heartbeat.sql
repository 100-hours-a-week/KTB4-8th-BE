-- 실행 인스턴스가 죽은 동기화를 다른 인스턴스가 회수할 수 있게 heartbeat를 기록한다.
alter table youtube_sync add column heartbeat_at datetime(6) not null default current_timestamp(6);

-- 분석 이력이 어느 동기화에서 시작됐는지 연결해, 만료된 동기화의 분석만 FAILED로 마감한다.
alter table video_analysis add column youtube_sync_id bigint null;
create index idx_video_analysis_youtube_sync on video_analysis (youtube_sync_id);
