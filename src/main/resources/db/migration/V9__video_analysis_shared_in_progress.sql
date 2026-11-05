-- 영상 분석을 시스템 전체에서 공유하기 위해 같은 영상의 진행 중 분석을 하나로 제한한다.
-- 이 유일 제약이 인스턴스 사이의 영상별 분석 소유권 경쟁을 정리한다.

-- 끝날 수 없는 진행 중 행(동기화 없음, 종료·삭제된 동기화)은 유일 제약보다 먼저 FAILED로 마감한다. 행은 삭제하지 않는다.
update video_analysis a
   set a.status = 'FAILED',
       a.completed_at = utc_timestamp(6)
 where a.status = 'IN_PROGRESS'
   and (a.youtube_sync_id is null
        or not exists (
            select 1 from youtube_sync s
             where s.id = a.youtube_sync_id and s.status in ('QUEUED','RUNNING')));

-- 실행 중인 동기화 사이에 같은 영상의 진행 중 행이 남아 있으면 ID가 가장 큰 행만 남기고 나머지를 FAILED로 마감한다.
update video_analysis a
  join (select video_id, max(id) as keep_id
          from video_analysis
         where status = 'IN_PROGRESS'
         group by video_id
        having count(*) > 1) d
    on d.video_id = a.video_id
   set a.status = 'FAILED',
       a.completed_at = utc_timestamp(6)
 where a.status = 'IN_PROGRESS'
   and a.id <> d.keep_id;

-- YouTube 영상 ID는 대소문자를 구분하므로 유일 제약은 바이트 단위로 비교한다.
alter table video_analysis
    add column in_progress_video_id varchar(255) character set utf8mb4 collate utf8mb4_0900_bin generated always as (
        case when status = 'IN_PROGRESS' then video_id else null end
    ) stored,
    add unique key uk_video_analysis_in_progress_video (in_progress_video_id),
    add key idx_video_analysis_video (video_id);
