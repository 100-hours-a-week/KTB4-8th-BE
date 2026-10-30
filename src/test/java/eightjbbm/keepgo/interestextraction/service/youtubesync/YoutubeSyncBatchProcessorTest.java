package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

@DisplayName("YouTube 동기화 배치 처리")
class YoutubeSyncBatchProcessorTest {

    private final AiCallGateway gateway = mock(AiCallGateway.class);
    private final YoutubeSyncBatchWriter batchWriter = mock(YoutubeSyncBatchWriter.class);
    private final VideoAnalysisManager videoAnalysisManager = mock(VideoAnalysisManager.class);
    private final YoutubeSyncBatchProcessor processor =
            new YoutubeSyncBatchProcessor(gateway, batchWriter, videoAnalysisManager);

    @Test
    @DisplayName("재시도가 모두 실패한 배치는 FAILED로 기록한다")
    void recordsBatchFailedWhenRetriesAreExhausted() {
        when(gateway.submitAnalysis(anyLong(), anyList(), any()))
                .thenReturn(completedFuture(
                        new AnalyzeVideoResponseV2.Failure("failure", new AnalyzeVideoResponseV2.Failure.Data())));
        doThrow(new CannotAcquireLockException("deadlock"))
                .when(batchWriter)
                .commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyInt());

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0"), 10);

        verify(batchWriter).commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyInt());
        verify(batchWriter).recordFailed(1L, "instance-a", List.of("v0"), 100);
    }
}
