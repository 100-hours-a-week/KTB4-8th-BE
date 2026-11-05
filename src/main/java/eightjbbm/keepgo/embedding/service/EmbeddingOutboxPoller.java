package eightjbbm.keepgo.embedding.service;

import eightjbbm.keepgo.embedding.EmbeddingOutboxProperties;
import eightjbbm.keepgo.embedding.entity.EmbeddingOutboxEntry;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/// 임베딩 아웃박스를 주기적으로 처리한다.
///
/// 처리권은 DB 조건부 UPDATE가 결정하므로 폴러가 여러 인스턴스에 있어도 같은 행을 동시에
/// 처리하지 않는다. 임베딩 호출은 트랜잭션 밖에서 하고, 최소 한 번 실행을 보장한다.
@Slf4j
@Service
public class EmbeddingOutboxPoller {

    static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final EmbeddingOutboxService outboxService;
    private final OutingGuideRepository outingGuideRepository;
    private final AiCallGateway aiCallGateway;
    private final EmbeddingOutboxProperties properties;
    private final Counter embedded;
    private final Counter retried;
    private final Counter failed;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmbeddingOutboxPoller(
            EmbeddingOutboxService outboxService,
            OutingGuideRepository outingGuideRepository,
            AiCallGateway aiCallGateway,
            EmbeddingOutboxProperties properties,
            MeterRegistry meterRegistry) {
        this.outboxService = outboxService;
        this.outingGuideRepository = outingGuideRepository;
        this.aiCallGateway = aiCallGateway;
        this.properties = properties;
        this.embedded = Counter.builder("keepgo.embedding.outbox.processed")
                .tag("result", "success")
                .register(meterRegistry);
        this.retried = Counter.builder("keepgo.embedding.outbox.processed")
                .tag("result", "retry")
                .register(meterRegistry);
        this.failed = Counter.builder("keepgo.embedding.outbox.processed")
                .tag("result", "failed")
                .register(meterRegistry);
    }

    @Scheduled(
            scheduler = "embeddingOutboxTaskScheduler",
            fixedDelayString = "${keepgo.embedding-outbox.poll-interval:5s}")
    public void poll() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            pollOnce();
        } catch (RuntimeException error) {
            log.error("임베딩 아웃박스 폴링 실패", error);
        } finally {
            running.set(false);
        }
    }

    /// 처리 시각이 된 행을 한 번만 처리한다. 테스트와 운영 점검에서 직접 호출한다.
    public int pollOnce() {
        List<EmbeddingOutboxEntry> due = outboxService.findDue(properties.batchSize());
        int processed = 0;
        for (EmbeddingOutboxEntry entry : due) {
            if (process(entry)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean process(EmbeddingOutboxEntry entry) {
        int attempt = entry.getAttemptCount() + 1;
        if (!outboxService.claim(
                entry.getGuideId(),
                entry.getAttemptCount(),
                Math.max(1, properties.claimDuration().toSeconds()))) {
            return false;
        }

        try {
            Optional<OutingGuide> guide = outingGuideRepository.findById(entry.getGuideId());
            if (guide.isEmpty()) {
                // 가이드가 사라졌으면 임베딩할 대상도 없다. FK cascade가 이미 지웠어야 하지만 방어한다.
                outboxService.complete(entry.getGuideId(), attempt);
                return true;
            }

            aiCallGateway.submitEmbedding(List.of(guide.get())).join();
            outboxService.complete(entry.getGuideId(), attempt);
            embedded.increment();
            return true;
        } catch (CompletionException error) {
            // last_error에 CompletionException이 아닌 실제 AI 호출 실패를 남긴다.
            RuntimeException cause = error.getCause() instanceof RuntimeException runtime ? runtime : error;
            handleFailure(entry.getGuideId(), attempt, cause);
            return true;
        } catch (RuntimeException error) {
            handleFailure(entry.getGuideId(), attempt, error);
            return true;
        }
    }

    private void handleFailure(Long guideId, int attempt, RuntimeException error) {
        String lastError = describe(error);
        if (attempt >= properties.maxAttempts()) {
            outboxService.markFailed(guideId, attempt, lastError);
            failed.increment();
            log.error("임베딩 아웃박스 처리를 포기합니다: guideId={}, attempts={}, error={}", guideId, attempt, lastError, error);
            return;
        }

        Duration backoff = properties.backoffAfter(attempt);
        outboxService.scheduleRetry(guideId, attempt, backoff.toSeconds(), lastError);
        retried.increment();
        log.warn(
                "임베딩 아웃박스 처리 실패, {} 뒤 다시 시도합니다: guideId={}, attempts={}, error={}",
                backoff,
                guideId,
                attempt,
                lastError,
                error);
    }

    private static String describe(RuntimeException error) {
        String message =
                error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
        return message.length() <= LAST_ERROR_MAX_LENGTH ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
