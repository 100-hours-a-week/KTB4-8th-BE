package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.services.youtube.model.*;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.*;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.GuideKey;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@Timeout(15)
class DefaultYoutubeVideoAnalyzerTest {
    private AiServerService ai;
    private OutingGuideRepository guides;
    private VideoAnalysisManager manager;
    private DefaultYoutubeVideoAnalyzer analyzer;
    private Map<GuideKey, OutingGuide> stored;

    @BeforeEach
    void setUp() {
        ai = mock(AiServerService.class);
        guides = mock(OutingGuideRepository.class);
        manager = mock(VideoAnalysisManager.class);
        analyzer = new DefaultYoutubeVideoAnalyzer(ai, guides, manager);
        stored = new HashMap<>();
        when(manager.start(anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            Map<String, Long> result = new HashMap<>();
            ids.forEach(id -> result.put(id, analysisId(id)));
            return result;
        });
        // Model read-after-write: analyze's unchanged return queries saved guides again.
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
    void splitsIntoTenAndCallsConcurrentlyOnPlatformThreads() {
        CountDownLatch entered = new CountDownLatch(3);
        Set<Long> threads = ConcurrentHashMap.newKeySet();
        long caller = Thread.currentThread().threadId();
        when(ai.analyzeVideoV2(anyList())).thenAnswer(call -> {
            List<String> batch = call.getArgument(0);
            assertFalse(Thread.currentThread().isVirtual());
            assertNotEquals(caller, Thread.currentThread().threadId());
            threads.add(Thread.currentThread().threadId());
            verify(manager).start(batch);
            entered.countDown();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "All batches must enter concurrently");
            return response(List.of());
        });
        assertTrue(analyzer.analyze(videos(21)).isEmpty());
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.captor();
        verify(ai, times(3)).analyzeVideoV2(batches.capture());
        assertEquals(Set.of(ids(0, 10), ids(10, 20), ids(20, 21)), new HashSet<>(batches.getAllValues()));
        assertEquals(3, threads.size());
    }

    @Test
    void failureResponseMarksWholeBatchFailed() {
        when(ai.analyzeVideoV2(anyList()))
                .thenReturn(new AnalyzeVideoResponseV2.Failure("failure", new AnalyzeVideoResponseV2.Failure.Data()));
        assertTrue(analyzer.analyze(videos(2)).isEmpty());
        verify(manager).completeUnsuccessful(Map.of(1L, VideoAnalysisStatus.FAILED, 2L, VideoAnalysisStatus.FAILED));
        verify(manager, never()).completeSuccessful(anyMap(), anyCollection());
    }

    @Test
    void requestExceptionMarksBatchFailedAndPropagates() {
        RuntimeException failure = new IllegalStateException("AI unavailable");
        when(ai.analyzeVideoV2(anyList())).thenThrow(failure);
        CompletionException thrown = assertThrows(CompletionException.class, () -> analyzer.analyze(videos(2)));
        assertSame(failure, thrown.getCause());
        verify(manager).completeUnsuccessful(Map.of(1L, VideoAnalysisStatus.FAILED, 2L, VideoAnalysisStatus.FAILED));
    }

    @Test
    void invalidVideosAreExcludedBeforeRequests() {
        Video privateVideo = video("v1");
        privateVideo.getStatus().setPrivacyStatus("private");
        Video longVideo = video("v2");
        longVideo.getContentDetails().setDuration("PT4M");
        when(ai.analyzeVideoV2(anyList())).thenReturn(response(List.of()));
        analyzer.analyze(List.of(video("v0"), privateVideo, longVideo, new Video()));
        verify(ai).analyzeVideoV2(List.of("v0"));
        verify(manager).start(List.of("v0"));
    }

    @Test
    void emptyInputDoesNotStartAnalysis() {
        assertTrue(analyzer.analyze(List.of()).isEmpty());
        verifyNoInteractions(manager, ai);
        verify(guides, never()).save(any(OutingGuide.class));
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
        return new GuideKey(guide.getName(), guide.getAddress());
    }

    private AnalyzeVideoResponseV2.Success response(List<AnalyzeVideoResponseV2.Success.Result> results) {
        return new AnalyzeVideoResponseV2.Success("ok", new AnalyzeVideoResponseV2.Success.Data(results));
    }

    private AnalyzeVideoResponseV2.Success.Result result(String id, String status, String name, String address) {
        return new AnalyzeVideoResponseV2.Success.Result(
                "https://youtube.com/watch/" + id,
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
