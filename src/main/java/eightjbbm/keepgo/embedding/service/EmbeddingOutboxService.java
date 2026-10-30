package eightjbbm.keepgo.embedding.service;

import eightjbbm.keepgo.embedding.entity.EmbeddingOutboxEntry;
import eightjbbm.keepgo.embedding.repository.EmbeddingOutboxRepository;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 임베딩 아웃박스 행을 기록하고 상태를 전이하는 트랜잭션 경계.
///
/// 임베딩 HTTP 호출이 트랜잭션을 감싸지 않도록, 폴러는 이 서비스의 짧은 트랜잭션만 호출한다.
@Service
@RequiredArgsConstructor
public class EmbeddingOutboxService {

    private final EmbeddingOutboxRepository outboxRepository;

    /// 신규 가이드의 임베딩 작업을 호출자의 트랜잭션에 함께 기록한다.
    ///
    /// 배치 커밋이 롤백되면 아웃박스 행도 함께 사라진다.
    public void enqueue(List<OutingGuide> createdGuides) {
        for (OutingGuide guide : createdGuides) {
            outboxRepository.insertPending(guide.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<EmbeddingOutboxEntry> findDue(int limit) {
        return outboxRepository.findDue(limit);
    }

    @Transactional
    public boolean claim(Long guideId, int expectedAttempts, long claimSeconds) {
        return outboxRepository.claim(guideId, expectedAttempts, claimSeconds) > 0;
    }

    @Transactional
    public void complete(Long guideId, int attempt) {
        outboxRepository.deletePending(guideId, attempt);
    }

    @Transactional
    public void scheduleRetry(Long guideId, int attempt, long backoffSeconds, String lastError) {
        outboxRepository.scheduleRetry(guideId, attempt, backoffSeconds, lastError);
    }

    @Transactional
    public void markFailed(Long guideId, int attempt, String lastError) {
        outboxRepository.markFailed(guideId, attempt, lastError);
    }
}
