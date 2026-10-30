package eightjbbm.keepgo.interestextraction.service.youtubesync;

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
import eightjbbm.keepgo.util.client.ai.job.AiRequestException;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisJobClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

@DisplayName("YouTube 동기화 배치 처리")
class YoutubeSyncBatchProcessorTest {

    private final VideoAnalysisJobClient videoAnalysisJobClient = mock(VideoAnalysisJobClient.class);
    private final YoutubeSyncBatchWriter batchWriter = mock(YoutubeSyncBatchWriter.class);
    private final YoutubeSyncBatchProcessor processor =
            new YoutubeSyncBatchProcessor(videoAnalysisJobClient, batchWriter);

    @Test
    @DisplayName("재시도가 모두 실패한 배치는 FAILED로 기록한다")
    void recordsBatchFailedWhenRetriesAreExhausted() {
        when(videoAnalysisJobClient.analyzeVideo(anyList(), any(Duration.class)))
                .thenThrow(new AiRequestException(AiRequestException.Kind.TIMEOUT, null, null, null));
        doThrow(new CannotAcquireLockException("deadlock"))
                .when(batchWriter)
                .commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyInt());

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0"), 10);

        verify(batchWriter).commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyInt());
        verify(batchWriter).recordFailed(1L, "instance-a", List.of("v0"), 100);
    }
}
