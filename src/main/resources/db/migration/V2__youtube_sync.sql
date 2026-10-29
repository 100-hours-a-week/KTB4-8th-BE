-- 회원당 하나만 실행되는 YouTube 동기화 상태. SSE 연결은 이 행을 폴링해 진척도를 전달한다.
create table youtube_sync_seq (
    next_val bigint
) engine=InnoDB;
insert into youtube_sync_seq ( next_val ) values ( 1 );
create table youtube_sync (
    id bigint not null,
    member_id bigint not null,
    instance_id varchar(64) not null,
    trace_id varchar(36) not null,
    status enum ('QUEUED','RUNNING','SUCCEEDED','FAILED') not null,
    event_seq bigint not null,
    video_fetched_count integer,
    video_target_count integer,
    video_analyzed_count integer,
    video_failed_count integer,
    progress integer not null,
    error_code varchar(64),
    create_datetime datetime(6) not null,
    update_datetime datetime(6) not null,
    finish_datetime datetime(6),
    -- 진행 중인 동기화만 회원 ID를 가진다. 유일 제약으로 회원당 하나를 DB가 보장한다.
    active_member_id bigint generated always as (
        case when status in ('QUEUED','RUNNING') then member_id else null end
    ) stored,
    primary key (id),
    unique key uk_youtube_sync_active_member (active_member_id),
    key idx_youtube_sync_member_finish (member_id, finish_datetime)
) engine=InnoDB;
