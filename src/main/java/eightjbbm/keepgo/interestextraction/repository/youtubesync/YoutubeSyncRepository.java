package eightjbbm.keepgo.interestextraction.repository.youtubesync;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface YoutubeSyncRepository extends JpaRepository<YoutubeSync, Long> {

    Optional<YoutubeSync> findFirstByMemberIdAndStatusInOrderByIdDesc(
            Long memberId, Collection<YoutubeSyncStatus> statuses);

    Optional<YoutubeSync> findFirstByMemberIdAndStatusInAndFinishedAtAfterOrderByIdDesc(
            Long memberId, Collection<YoutubeSyncStatus> statuses, Instant finishAfter);

    long countByInstanceIdAndStatusIn(String instanceId, Collection<YoutubeSyncStatus> statuses);

    /// 배치 완료 콜백은 병렬로 오므로 한 UPDATE로 누적한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update YoutubeSync s
               set s.videoAnalyzedCount = coalesce(s.videoAnalyzedCount, 0) + :processedCount,
                   s.videoFailedCount = coalesce(s.videoFailedCount, 0) + :failedCount,
                   s.progress = case when s.progress < :progress then :progress else s.progress end,
                   s.eventSeq = s.eventSeq + 1,
                   s.updatedAt = :now
             where s.id = :syncId
            """)
    int recordBatch(Long syncId, int processedCount, int failedCount, int progress, Instant now);
}
