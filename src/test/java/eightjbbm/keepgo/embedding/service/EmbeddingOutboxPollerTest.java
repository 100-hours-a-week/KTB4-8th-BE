package eightjbbm.keepgo.embedding.service;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.embedding.EmbeddingOutboxProperties;
import eightjbbm.keepgo.embedding.entity.EmbeddingOutboxEntry;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("임베딩 아웃박스 폴러")
class EmbeddingOutboxPollerTest {

    private static final long GUIDE_ID = 7L;

    private final EmbeddingOutboxService outboxService = mock(EmbeddingOutboxService.class);
    private final OutingGuideRepository guideRepository = mock(OutingGuideRepository.class);
    private final AiCallGateway aiCallGateway = mock(AiCallGateway.class);
    private final EmbeddingOutboxProperties properties = new EmbeddingOutboxProperties(
            Duration.ofSeconds(5), 10, Duration.ofMinutes(5), 2, List.of(Duration.ofSeconds(30)));
    private final EmbeddingOutboxPoller poller = new EmbeddingOutboxPoller(
            outboxService, guideRepository, aiCallGateway, properties, new SimpleMeterRegistry());

    @Test
    @DisplayName("마지막 시도가 실패하면 재시도하지 않고 FAILED로 표시한다")
    void lastFailedAttemptIsMarkedFailedInsteadOfRetried() {
        var entry = entry(1);
        when(outboxService.findDue(10)).thenReturn(List.of(entry));
        when(outboxService.claim(GUIDE_ID, 1, 300)).thenReturn(true);
        when(guideRepository.findById(GUIDE_ID)).thenReturn(Optional.of(mock(OutingGuide.class)));
        when(aiCallGateway.submitEmbedding(anyList())).thenReturn(failedFuture(new RuntimeException("AI down")));

        assertThat(poller.pollOnce()).isEqualTo(1);

        verify(outboxService).markFailed(GUIDE_ID, 2, "RuntimeException: AI down");
        verify(outboxService, never()).scheduleRetry(anyLong(), anyInt(), anyLong(), anyString());
        verify(outboxService, never()).complete(anyLong(), anyInt());
    }

    @Test
    @DisplayName("남은 시도가 있으면 backoff 후 재시도로 예약한다")
    void earlierFailureIsScheduledForRetry() {
        var entry = entry(0);
        when(outboxService.findDue(10)).thenReturn(List.of(entry));
        when(outboxService.claim(GUIDE_ID, 0, 300)).thenReturn(true);
        when(guideRepository.findById(GUIDE_ID)).thenReturn(Optional.of(mock(OutingGuide.class)));
        when(aiCallGateway.submitEmbedding(anyList())).thenReturn(failedFuture(new RuntimeException("AI down")));

        poller.pollOnce();

        verify(outboxService).scheduleRetry(eq(GUIDE_ID), eq(1), eq(30L), anyString());
        verify(outboxService, never()).markFailed(anyLong(), anyInt(), anyString());
    }

    private EmbeddingOutboxEntry entry(int attemptCount) {
        var entry = mock(EmbeddingOutboxEntry.class);
        when(entry.getGuideId()).thenReturn(GUIDE_ID);
        when(entry.getAttemptCount()).thenReturn(attemptCount);
        return entry;
    }
}
