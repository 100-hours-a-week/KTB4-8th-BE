-- Baseline of the current JPA mappings and JDBC OAuth client store.
-- Do not rewrite applied migrations. Add versioned, additive migrations instead.
-- Guide key cleanup and NOT NULL/unique changes belong to the later cutover.

create table business_schedule (
    estimated bit not null,
    id bigint not null,
    timezone varchar(255),
    primary key (id)
) engine=InnoDB;
create table business_schedule_exception (
    date date,
    end_time time(0),
    start_time time(0),
    business_schedule_id bigint,
    id bigint not null,
    primary key (id)
) engine=InnoDB;
create table business_schedule_exception_seq (
    next_val bigint
) engine=InnoDB;
insert into business_schedule_exception_seq ( next_val ) values ( 1 );
create table business_schedule_seq (
    next_val bigint
) engine=InnoDB;
insert into business_schedule_seq ( next_val ) values ( 1 );
create table business_schedule_weekly (
    end_time time(0),
    start_time time(0),
    business_schedule_id bigint,
    id bigint not null,
    day_of_week enum ('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY'),
    primary key (id)
) engine=InnoDB;
create table business_schedule_weekly_seq (
    next_val bigint
) engine=InnoDB;
insert into business_schedule_weekly_seq ( next_val ) values ( 1 );
create table chat (
    is_by_bot bit,
    created_at datetime(6),
    deleted_at datetime(6),
    id bigint not null,
    member_id bigint not null,
    content varchar(255),
    primary key (id)
) engine=InnoDB;
create table chat_seq (
    next_val bigint
) engine=InnoDB;
insert into chat_seq ( next_val ) values ( 1 );
create table file (
    id bigint not null,
    bucket varchar(255),
    object_key varchar(255),
    region varchar(255),
    storage_path varchar(255),
    storage_type enum ('LOCAL','S3'),
    primary key (id)
) engine=InnoDB;
create table file_seq (
    next_val bigint
) engine=InnoDB;
insert into file_seq ( next_val ) values ( 1 );
create table member (
    deleted_at datetime(6),
    id bigint not null,
    image_id bigint,
    liked_videos_playlist_id varchar(255),
    nickname varchar(255),
    primary key (id)
) engine=InnoDB;
create table member_liked_video_seq (
    next_val bigint
) engine=InnoDB;
insert into member_liked_video_seq ( next_val ) values ( 1 );
create table member_seq (
    next_val bigint
) engine=InnoDB;
insert into member_seq ( next_val ) values ( 1 );
create table member_liked_video (
    create_datetime datetime(6),
    id bigint not null,
    member_id bigint,
    video_id varchar(255),
    primary key (id)
) engine=InnoDB;
create table oauth_account_seq (
    next_val bigint
) engine=InnoDB;
insert into oauth_account_seq ( next_val ) values ( 1 );
create table oauth_account (
    created_at datetime(6),
    deleted_at datetime(6),
    id bigint not null,
    last_login_at datetime(6),
    member_id bigint,
    updated_at datetime(6),
    email varchar(255),
    issuer varchar(255),
    name varchar(255),
    refresh_token_encrypted varchar(255),
    subject varchar(255),
    primary key (id)
) engine=InnoDB;
create table outing_collection_private (
    course_id bigint,
    created_at datetime(6) not null,
    deleted_at datetime(6),
    guide_id bigint,
    id bigint not null,
    member_id bigint not null,
    type enum ('EVENT','OUTING_COURSE','OUTING_WISHLIST_SHARED','PLACE') not null,
    primary key (id)
) engine=InnoDB;
create table outing_collection_private_seq (
    next_val bigint
) engine=InnoDB;
insert into outing_collection_private_seq ( next_val ) values ( 1 );
create table outing_course_seq (
    next_val bigint
) engine=InnoDB;
insert into outing_course_seq ( next_val ) values ( 1 );
create table outing_guide_seq (
    next_val bigint
) engine=InnoDB;
insert into outing_guide_seq ( next_val ) values ( 1 );
create table outing_course (
    duration_hours integer not null,
    recommendation_score integer not null,
    create_datetime datetime(6),
    id bigint not null,
    location varchar(255),
    name varchar(255),
    primary key (id)
) engine=InnoDB;
create table outing_guide (
    lat float(23),
    lng float(23),
    end_datetime datetime(6),
    id bigint not null,
    image_id bigint,
    start_datetime datetime(6),
    type varchar(31) not null,
    address varchar(255),
    category varchar(255),
    description varchar(255),
    location_detail varchar(255),
    name varchar(255),
    business_schedule json,
    primary key (id),
    check ((type in ('event','place')))
) engine=InnoDB;
create table outing_stop (
    sequence integer not null,
    travel_minutes integer,
    outing_course_id bigint not null,
    outing_guide_id bigint not null,
    primary key (sequence, outing_course_id)
) engine=InnoDB;
create table video_analysis_seq (
    next_val bigint
) engine=InnoDB;
insert into video_analysis_seq ( next_val ) values ( 1 );
create table video_analysis (
    create_datetime datetime(6),
    id bigint not null,
    video_id varchar(255),
    status enum ('FAILED','IN_PROGRESS','NOT_FOUND','SUCCESS'),
    primary key (id)
) engine=InnoDB;
create table video_analysis_result (
    outing_guide_id bigint not null,
    video_analysis_id bigint not null,
    primary key (outing_guide_id, video_analysis_id)
) engine=InnoDB;
alter table member add constraint UKgdij511c54mr1s6fpej9m7jpu unique (image_id);
alter table outing_guide add constraint UKnljn68gpeheyrxa6s1yk5dv53 unique (image_id);
alter table business_schedule_exception add constraint FKifojuny1slaoqvbdfoscqpyhp foreign key (business_schedule_id) references business_schedule (id);
alter table business_schedule_weekly add constraint FKjfnb94v12sdw68qqq0qdmnvhq foreign key (business_schedule_id) references business_schedule (id);
alter table chat add constraint FKgvc5hrt0h18xk63qosss3ti30 foreign key (member_id) references member (id);
alter table member add constraint FKc8wj3hm04pxj3bv8yrxcdq9kf foreign key (image_id) references file (id);
alter table member_liked_video add constraint FK3ydrspnfl78xnfbr82e46ejfv foreign key (member_id) references member (id);
alter table oauth_account add constraint FK75dxauq72cuflu7n4seacd4rf foreign key (member_id) references member (id);
alter table outing_collection_private add constraint FKhml1b2t2ovg2rxtyxt4dkti1a foreign key (member_id) references member (id);
alter table outing_collection_private add constraint FKkjbv2e5jyqxektdgbt6hluda5 foreign key (course_id) references outing_course (id);
alter table outing_collection_private add constraint FK2wcdb104l2fop11egmus86f3b foreign key (guide_id) references outing_guide (id);
alter table outing_guide add constraint FKpfh7dygat90abdrtkxnxckuvx foreign key (image_id) references file (id);
alter table outing_stop add constraint FK43unulol2h2jhkadscaojmbk9 foreign key (outing_course_id) references outing_course (id);
alter table outing_stop add constraint FKb288o3j3kpuovf1qip6jcfll1 foreign key (outing_guide_id) references outing_guide (id);
alter table video_analysis_result add constraint FKgn1pwtservii7q5bk6bww2g8x foreign key (outing_guide_id) references outing_guide (id);
alter table video_analysis_result add constraint FKr463r25glv74uq8jlfvgqn1ap foreign key (video_analysis_id) references video_analysis (id);

CREATE TABLE oauth2_authorized_client (
                                          client_registration_id varchar(100) NOT NULL,
                                          principal_name varchar(200) NOT NULL,
                                          access_token_type varchar(100) NOT NULL,
                                          access_token_value blob NOT NULL,
                                          access_token_issued_at timestamp NOT NULL,
                                          access_token_expires_at timestamp NOT NULL,
                                          access_token_scopes varchar(1000) DEFAULT NULL,
                                          refresh_token_value blob DEFAULT NULL,
                                          refresh_token_issued_at timestamp DEFAULT NULL,
                                          created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                          PRIMARY KEY (client_registration_id, principal_name)
);
