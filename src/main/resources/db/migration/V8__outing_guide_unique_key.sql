-- 나들이 가이드의 (유형, 이름, 도로명주소) 중복을 대표 ID 하나로 정리하고 DB에서 유일성을 보장한다.
-- 잘못된 데이터가 있으면 데이터를 바꾸지 않고 중단하며, 대상 ID와 참조 수를 보고 테이블에 남긴다.
-- 대표가 아닌 ID와 이미지는 outing_guide_dedup_map에 남겨 AI 벡터 삭제에 사용한다.

drop table if exists outing_guide_dedup_invalid;
create table outing_guide_dedup_invalid (
    guide_id bigint not null,
    reason varchar(32) not null,
    wishlist_reference_count bigint not null,
    course_stop_reference_count bigint not null,
    analysis_reference_count bigint not null,
    primary key (guide_id)
) engine=InnoDB;

insert into outing_guide_dedup_invalid (
    guide_id, reason, wishlist_reference_count, course_stop_reference_count, analysis_reference_count
)
select g.id,
       case
           when g.name is null then 'NULL_NAME'
           when trim(g.name) = '' then 'BLANK_NAME'
           when char_length(g.name) > 255 then 'LONG_NAME'
           when g.address is null then 'NULL_ADDRESS'
           when trim(g.address) = '' then 'BLANK_ADDRESS'
           else 'LONG_ADDRESS'
       end,
       (select count(*) from outing_collection_private w where w.guide_id = g.id),
       (select count(*) from outing_stop s where s.outing_guide_id = g.id),
       (select count(*) from video_analysis_result r where r.outing_guide_id = g.id)
from outing_guide g
where g.name is null
   or trim(g.name) = ''
   or char_length(g.name) > 255
   or g.address is null
   or trim(g.address) = ''
   or char_length(g.address) > 255;

-- 미해결 행이 있으면 CHECK 위반으로 migration을 중단한다. 행은 위 보고 테이블에 남는다.
drop table if exists outing_guide_dedup_guard;
create table outing_guide_dedup_guard (
    invalid_row_count bigint not null,
    constraint chk_outing_guide_dedup_guard check (invalid_row_count = 0)
) engine=InnoDB;
insert into outing_guide_dedup_guard (invalid_row_count)
select count(*) from outing_guide_dedup_invalid;
drop table outing_guide_dedup_guard;

-- 대표는 같은 키의 최소 ID이며, 대표 행의 필드는 병합하지 않는다.
-- 이름·주소는 collation 영향 없이 바이트 단위로 비교한다.
create table outing_guide_dedup_map (
    guide_id bigint not null,
    representative_id bigint not null,
    image_id bigint,
    primary key (guide_id),
    key idx_outing_guide_dedup_map_representative (representative_id)
) engine=InnoDB;

insert into outing_guide_dedup_map (guide_id, representative_id, image_id)
select g.id,
       (select min(r.id)
        from outing_guide r
        where cast(r.type as binary) = cast(g.type as binary)
          and cast(r.name as binary) = cast(g.name as binary)
          and cast(r.address as binary) = cast(g.address as binary)),
       g.image_id
from outing_guide g;

update outing_stop s
join outing_guide_dedup_map m on m.guide_id = s.outing_guide_id
set s.outing_guide_id = m.representative_id
where m.guide_id <> m.representative_id;

update outing_collection_private w
join outing_guide_dedup_map m on m.guide_id = w.guide_id
set w.guide_id = m.representative_id
where m.guide_id <> m.representative_id;

-- 복합 PK 충돌을 피하려고 대표 ID 기준으로 매핑을 다시 만든다.
drop table if exists video_analysis_result_dedup;
create table video_analysis_result_dedup like video_analysis_result;
insert into video_analysis_result_dedup (outing_guide_id, video_analysis_id)
select distinct m.representative_id, r.video_analysis_id
from video_analysis_result r
join outing_guide_dedup_map m on m.guide_id = r.outing_guide_id;
delete from video_analysis_result;
insert into video_analysis_result (outing_guide_id, video_analysis_id)
select outing_guide_id, video_analysis_id from video_analysis_result_dedup;
drop table video_analysis_result_dedup;

-- 같은 회원·대표 가이드의 활성 위시리스트는 최소 ID만 남기고 나머지는 삭제 이력을 남겨 처리한다.
update outing_collection_private w
join (
    select member_id, guide_id, min(id) as keep_id
    from outing_collection_private
    where deleted_at is null and guide_id is not null
    group by member_id, guide_id
    having count(*) > 1
) duplicates
    on duplicates.member_id = w.member_id
   and duplicates.guide_id = w.guide_id
set w.deleted_at = utc_timestamp(6)
where w.deleted_at is null and w.id <> duplicates.keep_id;

-- 대표가 아닌 가이드 행만 삭제한다. 이미지(file)는 삭제하지 않고 map에 보고한다.
delete g
from outing_guide g
join outing_guide_dedup_map m on m.guide_id = g.id
where m.guide_id <> m.representative_id;

alter table outing_guide
    modify column `type` varchar(31) character set utf8mb4 collate utf8mb4_0900_bin not null,
    modify column name varchar(255) character set utf8mb4 collate utf8mb4_0900_bin not null,
    modify column address varchar(255) character set utf8mb4 collate utf8mb4_0900_bin not null,
    add constraint uk_outing_guide_type_name_address unique (`type`, name, address);
