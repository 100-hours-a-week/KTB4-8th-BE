package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.services.youtube.model.*;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.*;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.client.ai.analysis.*;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@Timeout(15)
@DisplayName("기본 YouTube 영상 분석기")
class DefaultYoutubeVideoAnalyzerTest {
    private AiCallGateway gateway;
    private OutingGuideRepository guides;
    private VideoAnalysisManager manager;
    private DefaultYoutubeVideoAnalyzer analyzer;
    private Map<GuideKey, OutingGuide> stored;

    @BeforeEach
    void setUp() {
        gateway = mock(AiCallGateway.class);
        guides = mock(OutingGuideRepository.class);
        manager = mock(VideoAnalysisManager.class);
        analyzer = new DefaultYoutubeVideoAnalyzer(gateway, guides, manager);
        stored = new HashMap<>();
        when(manager.start(anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            ids.forEach(id -> result.put(id, analysisId(id)));
            return result;
        });
        when(guides.findAllByAddresses(anyList())).thenAnswer(call -> {
            List<?> addresses = call.getArgument(0);
            return stored.values().stream()
                    .filter(g -> addresses.contains(g.getAddress()))
                    .toList();
        });
        when(guides.save(any(OutingGuide.class))).thenAnswer(call -> {
            OutingGuide guide = call.getArgument(0);
            ReflectionTestUtils.setField(guide, "id", (long) stored.size() + 1);
            stored.put(key(guide), guide);
            return guide;
        });
    }

    @Test
    @DisplayName("영상을 10개 단위 배치로 게이트웨이에 제출한다")
    void splitsIntoTenAndSubmitsThroughGateway() {
        givenAnalysis(response(List.of()));

        assertTrue(analyzer.analyze(videos(21)).isEmpty());

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.captor();
        verify(gateway, times(3)).submitAnalysis(anyLong(), batches.capture(), any(Runnable.class));
        assertEquals(Set.of(ids(0, 10), ids(10, 20), ids(20, 21)), new HashSet<>(batches.getAllValues()));
        verify(manager, never()).start(anyLong(), anyList());
    }

    @Test
    @DisplayName("동기화 ID가 있으면 분석 이력 시작에 그대로 전달한다")
    void propagatesSyncIdToAnalysisStart() {
        long syncId = 42L;
        when(manager.start(eq(syncId), anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(1);
            Map<String, Long> result = new HashMap<>();
            ids.forEach(id -> result.put(id, analysisId(id)));
            return result;
        });
        givenAnalysis(response(List.of()));

        analyzer.analyze(syncId, videos(2), (processedCount, failedCount) -> {});

        verify(manager).start(eq(syncId), anyList());
        verify(manager, never()).start(anyList());
    }

    @Test
    @DisplayName("실패 응답은 배치 전체를 FAILED로 기록한다")
    void failureResponseMarksWholeBatchFailed() {
        givenAnalysis(new AnalyzeVideoResponseV2.Failure("failure", new AnalyzeVideoResponseV2.Failure.Data()));

        assertTrue(analyzer.analyze(videos(2)).isEmpty());

        verify(manager).completeUnsuccessful(Map.of(1L, VideoAnalysisStatus.FAILED, 2L, VideoAnalysisStatus.FAILED));
        verify(manager, never()).completeSuccessful(anyMap(), anyCollection());
    }

    @Test
    @DisplayName("AI 요청 예외는 배치를 FAILED로 기록하고 밖으로 던진다")
    void requestExceptionMarksBatchFailedAndPropagates() {
        RuntimeException failure = new IllegalStateException("AI unavailable");
        givenAnalysisFailure(failure);

        CompletionException thrown = assertThrows(CompletionException.class, () -> analyzer.analyze(videos(2)));

        assertSame(failure, thrown.getCause());
        verify(manager).completeUnsuccessful(Map.of(1L, VideoAnalysisStatus.FAILED, 2L, VideoAnalysisStatus.FAILED));
    }

    @Test
    @DisplayName("유효하지 않은 영상은 요청 전에 제외한다")
    void invalidVideosAreExcludedBeforeRequests() {
        Video privateVideo = video("v1");
        privateVideo.getStatus().setPrivacyStatus("private");
        Video longVideo = video("v2");
        longVideo.getContentDetails().setDuration("PT4M");
        givenAnalysis(response(List.of()));

        analyzer.analyze(List.of(video("v0"), privateVideo, longVideo, new Video()));

        verify(gateway).submitAnalysis(anyLong(), eq(List.of("v0")), any(Runnable.class));
        verify(manager).start(List.of("v0"));
    }

    @Test
    @DisplayName("입력이 비면 분석을 시작하지 않는다")
    void emptyInputDoesNotStartAnalysis() {
        assertTrue(analyzer.analyze(List.of()).isEmpty());
        verifyNoInteractions(manager, gateway);
        verify(guides, never()).save(any(OutingGuide.class));
    }

    @Test
    @DisplayName("성공한 분석 결과는 한 번만 저장하고 분석 이력과 연결한다")
    void persistsSuccessfulAnalysisOnce() {
        givenAnalysis(response(List.of(result("v0", "success", "장소", "도로명주소"))));
        when(gateway.submitEmbedding(anyList())).thenReturn(completedFuture(null));

        List<OutingGuide> persisted = analyzer.analyze(videos(1));

        assertThat(persisted).hasSize(1);
        verify(guides, times(1)).findAllByAddresses(anyList());
        verify(guides, times(1)).save(any(OutingGuide.class));
        verify(gateway, times(1)).submitEmbedding(anyList());
        verify(manager)
                .completeSuccessful(
                        argThat(analyses -> analyses.containsKey(1L)),
                        argThat(persistedGuides -> persistedGuides.size() == 1));
    }

    private void givenAnalysis(AnalyzeVideoResponseV2 response) {
        when(gateway.submitAnalysis(anyLong(), anyList(), any(Runnable.class))).thenAnswer(call -> {
            call.getArgument(2, Runnable.class).run();
            return completedFuture(response);
        });
    }

    private void givenAnalysisFailure(RuntimeException failure) {
        when(gateway.submitAnalysis(anyLong(), anyList(), any(Runnable.class))).thenAnswer(call -> {
            call.getArgument(2, Runnable.class).run();
            return failedFuture(failure);
        });
    }

    private List<String> ids(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> "v" + i).toList();
    }

    private List<Video> videos(int count) {
        return ids(0, count).stream().map(this::video).toList();
    }

    private Video video(String id) {
        return new Video()
                .setId(id)
                .setStatus(new VideoStatus().setUploadStatus("uploaded").setPrivacyStatus("public"))
                .setContentDetails(new VideoContentDetails().setDuration("PT1M"));
    }

    private long analysisId(String id) {
        return Long.parseLong(id.substring(1)) + 1;
    }

    private GuideKey key(OutingGuide guide) {
        return GuideKey.from(guide);
    }

    private AnalyzeVideoResponseV2.Success response(List<AnalyzeVideoResponseV2.Success.Result> results) {
        return new AnalyzeVideoResponseV2.Success("ok", new AnalyzeVideoResponseV2.Success.Data(results));
    }

    private AnalyzeVideoResponseV2.Success.Result result(String id, String status, String name, String address) {
        return new AnalyzeVideoResponseV2.Success.Result(
                "https://youtube.com/shorts/" + id,
                status,
                null,
                name,
                address,
                null,
                "카페",
                null,
                null,
                null,
                null,
                37.5f,
                127.0f,
                0.9f,
                "summary");
    }
}
