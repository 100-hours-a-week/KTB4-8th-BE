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

    /// 같은 요청 스레드의 1차 캐시에 갇히지 않도록 상태만 질의한다.
    @Query("""
            select new eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncState(
                       s.status, s.errorCode)
              from YoutubeSync s
             where s.id = :syncId
            """)
    Optional<YoutubeSyncState> findStateById(Long syncId);

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

    /// 실행 인스턴스가 맡은 QUEUED 행만 RUNNING으로 바꾼다.
    ///
    /// heartbeat가 끊겨 sweeper가 이미 FAILED로 마감한 행은 영향받은 행이 0개가 되어,
    /// 늦게 깨어난 이전 실행자가 동기화를 되살리지 못한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update YoutubeSync s
               set s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.RUNNING,
                   s.videoFetchedCount = :fetchedCount,
                   s.videoTargetCount = :targetCount,
                   s.videoAnalyzedCount = 0,
                   s.videoFailedCount = 0,
                   s.progress = case when s.progress < :progress then :progress else s.progress end,
                   s.eventSeq = s.eventSeq + 1,
                   s.updatedAt = :now
             where s.id = :syncId
               and s.instanceId = :instanceId
               and s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.QUEUED
            """)
    int startFenced(Long syncId, String instanceId, int fetchedCount, int targetCount, int progress, Instant now);

    /// 배치 결과를 커밋하는 트랜잭션 안에서 진척도를 누적한다.
    ///
    /// 실행 인스턴스와 RUNNING 상태를 조건으로 하므로, 영향받은 행이 0개면 호출자가 롤백한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update YoutubeSync s
               set s.videoAnalyzedCount = coalesce(s.videoAnalyzedCount, 0) + :processedCount,
                   s.videoFailedCount = coalesce(s.videoFailedCount, 0) + :failedCount,
                   s.progress = case when s.progress < :progress then :progress else s.progress end,
                   s.eventSeq = s.eventSeq + 1,
                   s.updatedAt = :now
             where s.id = :syncId
               and s.instanceId = :instanceId
               and s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.RUNNING
            """)
    int recordBatchFenced(
            Long syncId, String instanceId, int processedCount, int failedCount, int progress, Instant now);

    /// 실행 인스턴스가 소유한 RUNNING 동기화만 성공으로 마감한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update YoutubeSync s
               set s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.SUCCEEDED,
                   s.progress = case when s.progress < :progress then :progress else s.progress end,
                   s.finishedAt = :now,
                   s.eventSeq = s.eventSeq + 1,
                   s.updatedAt = :now
             where s.id = :syncId
               and s.instanceId = :instanceId
               and s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.RUNNING
            """)
    int succeedFenced(Long syncId, String instanceId, int progress, Instant now);

    /// 실행 인스턴스가 소유한 QUEUED·RUNNING 동기화만 실패로 마감한다.
    ///
    /// sweeper가 먼저 FAILED로 마감한 행은 건드리지 않아 오류 코드와 종료 시각을 덮어쓰지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update YoutubeSync s
               set s.status = eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.FAILED,
                   s.errorCode = :errorCode,
                   s.finishedAt = :now,
                   s.eventSeq = s.eventSeq + 1,
                   s.updatedAt = :now
             where s.id = :syncId
               and s.instanceId = :instanceId
               and s.status in (
                   eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.QUEUED,
                   eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus.RUNNING
               )
            """)
    int failFenced(Long syncId, String instanceId, String errorCode, Instant now);
}
