package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import eightjbbm.keepgo.util.client.ai.job.AiRequestException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

@DisplayName("YouTube 동기화 배치 처리")
class YoutubeSyncBatchProcessorTest {

    private final AiCallGateway gateway = mock(AiCallGateway.class);
    private final YoutubeSyncBatchWriter batchWriter = mock(YoutubeSyncBatchWriter.class);
    private final VideoAnalysisManager videoAnalysisManager = mock(VideoAnalysisManager.class);
    private final SharedVideoAnalysisService sharedAnalysis = mock(SharedVideoAnalysisService.class);
    private final YoutubeSyncBatchProcessor processor =
            new YoutubeSyncBatchProcessor(gateway, batchWriter, videoAnalysisManager, sharedAnalysis);

    @Test
    @DisplayName("재시도가 모두 실패한 배치는 FAILED로 기록한다")
    void recordsBatchFailedWhenRetriesAreExhausted() {
        when(gateway.submitVideoAnalysis(anyLong(), anyList(), any()))
                .thenReturn(failedFuture(new AiRequestException(AiRequestException.Kind.TIMEOUT, null, null, null)));
        doThrow(new CannotAcquireLockException("deadlock"))
                .when(batchWriter)
                .commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyList(), anyInt());

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0"), 10);

        verify(batchWriter).commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyList(), anyInt());
        verify(batchWriter).recordFailed(1L, "instance-a", List.of("v0"), 100);
    }

    @Test
    @DisplayName("sweeper가 마감해 중단되면 이미 전송한 배치의 분석 중 기록을 FAILED로 정리한다")
    void failsInProgressAnalysesWhenFenced() {
        when(gateway.submitVideoAnalysis(anyLong(), anyList(), any()))
                .thenReturn(failedFuture(new AiRequestException(AiRequestException.Kind.TIMEOUT, null, null, null)));
        doThrow(new SyncFencedException(1L))
                .when(batchWriter)
                .commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyList(), anyInt());

        assertThatThrownBy(() -> processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0"), 10))
                .isInstanceOf(SyncFencedException.class);

        verify(videoAnalysisManager).failInProgress(1L);
    }

    @Test
    @DisplayName("실행권 판정 중 오류가 나면 배치 전체를 FAILED로 한 번에 커밋한다")
    void failsWholeBatchWhenAcquireExecutionRightThrows() {
        when(sharedAnalysis.acquireExecutionRight(1L, "v0"))
                .thenReturn(SharedVideoAnalysisService.AnalysisExecutionRight.won());
        when(sharedAnalysis.acquireExecutionRight(1L, "v1")).thenThrow(new IllegalStateException("acquire failed"));
        givenGatewayRunsBeforeSend();

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0", "v1"), 10);

        verify(batchWriter)
                .commit(
                        eq(1L),
                        eq("instance-a"),
                        eq(2L),
                        argThat(analysis ->
                                analysis.videoIds().equals(List.of("v0", "v1")) && analysis.failedCount() == 2),
                        eq(List.of()),
                        eq(100));
        verify(batchWriter, never()).recordFailed(anyLong(), anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("AI 호출이 실패해도 재사용·합류한 영상은 FAILED 이력을 만들지 않는다")
    void doesNotRecordFailedHistoryForReusedOrJoinedVideos() throws InterruptedException {
        SharedVideoResult reused = new SharedVideoResult("v1", VideoAnalysisStatus.NOT_FOUND, List.of());
        when(sharedAnalysis.acquireExecutionRight(1L, "v0"))
                .thenReturn(SharedVideoAnalysisService.AnalysisExecutionRight.won());
        when(sharedAnalysis.acquireExecutionRight(1L, "v1"))
                .thenReturn(SharedVideoAnalysisService.AnalysisExecutionRight.reusing(reused));
        when(sharedAnalysis.acquireExecutionRight(1L, "v2"))
                .thenReturn(SharedVideoAnalysisService.AnalysisExecutionRight.joining());
        when(sharedAnalysis.awaitResolved(List.of("v2"))).thenReturn(Map.of("v2", SharedVideoResult.failed("v2")));
        givenGatewayRunsBeforeSend();

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0", "v1", "v2"), 10);

        verify(batchWriter)
                .commit(
                        eq(1L),
                        eq("instance-a"),
                        eq(2L),
                        argThat(analysis -> analysis.videoIds().equals(List.of("v0"))),
                        eq(List.of(reused)),
                        anyInt());
        verify(batchWriter)
                .commit(
                        eq(1L),
                        eq("instance-a"),
                        eq(2L),
                        argThat(analysis -> analysis.videos().isEmpty()),
                        eq(List.of(SharedVideoResult.failed("v2"))),
                        eq(100));
    }

    @Test
    @DisplayName("공유 결과만 있는 배치의 커밋이 실패하면 분석 이력 없이 처리·실패 수만 기록한다")
    void recordsSharedFailureWithoutAnalysisHistory() {
        SharedVideoResult reused = new SharedVideoResult("v0", VideoAnalysisStatus.SUCCESS, List.of(7L));
        when(sharedAnalysis.findCompleted(anyCollection())).thenReturn(Map.of("v0", reused));
        doThrow(new CannotAcquireLockException("deadlock"))
                .when(batchWriter)
                .commit(anyLong(), anyString(), anyLong(), any(BatchAnalysis.class), anyList(), anyInt());

        processor.process(1L, "instance-a", 2L, YoutubeStub.videos("v0"), 10);

        verify(batchWriter).recordSharedFailed(1L, "instance-a", 1, 100);
        verify(batchWriter, never()).recordFailed(anyLong(), anyString(), anyList(), anyInt());
    }

    /// 실제 게이트웨이처럼 슬롯을 얻은 뒤 beforeSend를 실행하고, 전송 없이 AI 호출 실패로 끝낸다.
    private void givenGatewayRunsBeforeSend() {
        when(gateway.submitVideoAnalysis(anyLong(), anyList(), any())).thenAnswer(call -> {
            List<String> requested = call.getArgument(1);
            UnaryOperator<List<String>> beforeSend = call.getArgument(2);
            try {
                beforeSend.apply(requested);
            } catch (RuntimeException error) {
                return failedFuture(error);
            }
            return failedFuture(new AiRequestException(AiRequestException.Kind.TIMEOUT, null, null, null));
        });
    }
}
