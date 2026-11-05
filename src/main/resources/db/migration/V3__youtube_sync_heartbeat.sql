-- 실행 인스턴스가 죽은 동기화를 다른 인스턴스가 회수할 수 있게 heartbeat를 기록한다.
-- Hibernate는 Instant를 UTC DATETIME으로 쓰므로 기본값도 UTC로 맞춘다.
alter table youtube_sync add column heartbeat_at datetime(6) not null default (UTC_TIMESTAMP(6));

-- sweeper와 heartbeat가 자주 조회하는 조건을 인덱스로 받친다.
create index idx_youtube_sync_instance_status on youtube_sync (instance_id, status);
create index idx_youtube_sync_status_heartbeat on youtube_sync (status, heartbeat_at);

-- 분석 이력이 어느 동기화에서 시작됐는지 연결해, 만료된 동기화의 분석만 FAILED로 마감한다.
alter table video_analysis add column youtube_sync_id bigint null;
create index idx_video_analysis_youtube_sync on video_analysis (youtube_sync_id);

-- 롤링 배포 중 구 바이너리는 heartbeat_at을 갱신하지 않으므로 배포 순서를 조정한다.
-- 기존 IN_PROGRESS 분석은 youtube_sync_id가 null이라 안정적으로 backfill할 수 없다(백필하지 않는다).
