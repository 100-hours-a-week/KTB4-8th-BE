package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {

    /// 만료된 동기화에 속한 진행 중 분석만 실패로 마감한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VideoAnalysis a
               set a.status = eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus.FAILED,
                   a.completedAt = :completedAt
             where a.youtubeSyncId in :syncIds
               and a.status = eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus.IN_PROGRESS
            """)
    int markFailedBySyncIds(Collection<Long> syncIds, Instant completedAt);

    List<VideoAnalysis> findAllByYoutubeSyncIdAndVideoIdIn(Long youtubeSyncId, Collection<String> videoIds);

    /// 동기화와 무관하게 영상별 분석 이력을 모두 읽는다. 영상 분석 결과를 시스템 전체에서 공유하는 기준이다.
    List<VideoAnalysis> findAllByVideoIdIn(Collection<String> videoIds);

    /// 진행 중으로 남았지만 소유한 동기화가 이미 끝났거나 없는 분석의 동기화 ID. 합류한 대기자가 영원히 기다리지 않게 한다.
    @Query("""
            select distinct a.youtubeSyncId
              from VideoAnalysis a
             where a.videoId in :videoIds
               and a.status = eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus.IN_PROGRESS
               and a.youtubeSyncId is not null
               and not exists (
                   select 1 from YoutubeSync s
                    where s.id = a.youtubeSyncId
                      and s.status in (
                          eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.QUEUED,
                          eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.RUNNING))
            """)
    List<Long> findOrphanedSyncIds(Collection<String> videoIds);
}
