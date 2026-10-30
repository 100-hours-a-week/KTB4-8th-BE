-- 커밋된 나들이 가이드의 임베딩을 동기화 트랜잭션과 분리해 처리하는 아웃박스.
--
-- 신규 가이드를 만드는 트랜잭션에서 함께 기록되므로 롤백되면 행도 남지 않는다.
-- `next_attempt_at`을 조건부 UPDATE로 미루는 것이 처리권 확보다. 시각은 모두 DB 시간(UTC)이다.
create table outing_guide_embedding_outbox (
    guide_id bigint not null,
    status enum ('PENDING','FAILED') not null default 'PENDING',
    attempt_count integer not null default 0,
    next_attempt_at datetime(6) not null default (UTC_TIMESTAMP(6)),
    last_error varchar(1000),
    created_at datetime(6) not null default (UTC_TIMESTAMP(6)),
    updated_at datetime(6) not null default (UTC_TIMESTAMP(6)),
    primary key (guide_id),
    key idx_outing_guide_embedding_outbox_due (status, next_attempt_at),
    constraint fk_outing_guide_embedding_outbox_guide
        foreign key (guide_id) references outing_guide (id) on delete cascade
) engine=InnoDB;
