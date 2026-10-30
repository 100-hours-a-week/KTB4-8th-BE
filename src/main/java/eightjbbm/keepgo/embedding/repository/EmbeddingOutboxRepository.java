package eightjbbm.keepgo.embedding.repository;

import eightjbbm.keepgo.embedding.entity.EmbeddingOutboxEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmbeddingOutboxRepository extends JpaRepository<EmbeddingOutboxEntry, Long> {

    /// 신규 가이드의 임베딩 작업을 커밋 트랜잭션 안에서 기록한다. 시각은 DB 기본값을 쓴다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO outing_guide_embedding_outbox (guide_id) VALUES (:guideId)", nativeQuery = true)
    int insertPending(Long guideId);

    /// 처리 시각이 된 행을 오래 기다린 순서로 고른다. 실제 처리권은 `claim`이 결정한다.
    @Query(value = """
            SELECT *
              FROM outing_guide_embedding_outbox
             WHERE status = 'PENDING'
               AND next_attempt_at <= UTC_TIMESTAMP(6)
             ORDER BY next_attempt_at, guide_id
             LIMIT :limit
            """, nativeQuery = true)
    List<EmbeddingOutboxEntry> findDue(int limit);

    /// 이 폴러가 읽은 시도 횟수 그대로일 때만 다음 시도 시각을 미루고 시도 횟수를 늘린다.
    ///
    /// 같은 행을 읽은 폴러 두 개가 있어도 조건부 UPDATE에 성공한 하나만 처리권을 가진다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outing_guide_embedding_outbox
               SET attempt_count = attempt_count + 1,
                   next_attempt_at = TIMESTAMPADD(SECOND, :claimSeconds, UTC_TIMESTAMP(6)),
                   updated_at = UTC_TIMESTAMP(6)
             WHERE guide_id = :guideId
               AND attempt_count = :expectedAttempts
               AND status = 'PENDING'
               AND next_attempt_at <= UTC_TIMESTAMP(6)
            """, nativeQuery = true)
    int claim(Long guideId, int expectedAttempts, long claimSeconds);

    /// 임베딩에 성공한 행은 삭제한다. 처리권을 잃은 늦은 실행자의 삭제는 반영되지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM outing_guide_embedding_outbox
             WHERE guide_id = :guideId
               AND attempt_count = :attempt
               AND status = 'PENDING'
            """, nativeQuery = true)
    int deletePending(Long guideId, int attempt);

    /// 실패한 행의 다음 시도 시각을 backoff에 맞춰 미룬다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outing_guide_embedding_outbox
               SET next_attempt_at = TIMESTAMPADD(SECOND, :backoffSeconds, UTC_TIMESTAMP(6)),
                   last_error = :lastError,
                   updated_at = UTC_TIMESTAMP(6)
             WHERE guide_id = :guideId
               AND attempt_count = :attempt
               AND status = 'PENDING'
            """, nativeQuery = true)
    int scheduleRetry(Long guideId, int attempt, long backoffSeconds, String lastError);

    /// 최대 시도 횟수를 넘긴 행은 FAILED로 남겨 운영자가 확인할 수 있게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outing_guide_embedding_outbox
               SET status = 'FAILED',
                   last_error = :lastError,
                   updated_at = UTC_TIMESTAMP(6)
             WHERE guide_id = :guideId
               AND attempt_count = :attempt
               AND status = 'PENDING'
            """, nativeQuery = true)
    int markFailed(Long guideId, int attempt, String lastError);
}
