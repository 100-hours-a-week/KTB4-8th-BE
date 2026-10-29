package eightjbbm.keepgo.interestextraction.repository.youtubesync;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /// 이 인스턴스가 맡은 QUEUED·RUNNING 행만 DB 시간으로 heartbeat를 남긴다.
    ///
    /// `updated_at`과 `event_seq`는 바꾸지 않아 heartbeat 때문에 SSE 이벤트가 생기지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE youtube_sync
               SET heartbeat_at = UTC_TIMESTAMP(6)
             WHERE instance_id = :instanceId
               AND status IN ('QUEUED','RUNNING')
            """, nativeQuery = true)
    int heartbeat(String instanceId);

    /// heartbeat가 끊긴 행을 잠그고 ID만 가져온다. 다른 인스턴스가 잠근 행은 건너뛴다.
    ///
    /// 한 트랜잭션이 잠그는 행 수를 `LIMIT`으로 제한한다. 남은 행은 다음 sweep 주기에 처리한다.
    @Query(value = """
            SELECT id
              FROM youtube_sync
             WHERE status IN ('QUEUED','RUNNING')
               AND heartbeat_at < TIMESTAMPADD(SECOND, -:timeoutSeconds, UTC_TIMESTAMP(6))
             ORDER BY heartbeat_at
             LIMIT 1000
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findExpiredIdsForUpdate(int timeoutSeconds);

    /// 만료된 동기화를 DB 시간으로 실패 처리한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE youtube_sync
               SET status = 'FAILED',
                   error_code = :errorCode,
                   finished_at = UTC_TIMESTAMP(6),
                   updated_at = UTC_TIMESTAMP(6),
                   event_seq = event_seq + 1
             WHERE id IN (:ids)
            """, nativeQuery = true)
    int failExpired(Collection<Long> ids, String errorCode);

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
