package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultYoutubeVideoAnalyzer implements YoutubeVideoAnalyzer {

    private static final String SHORTS_PREFIX = "https://youtube.com/shorts/";

    private final AiCallGateway aiCallGateway;
    private final OutingGuideRepository outingGuideRepository;
    private final VideoAnalysisManager videoAnalysisManager;

    @Override
    public List<OutingGuide> analyze(List<Video> videos) {
        return analyze(videos, (processedCount, failedCount) -> {});
    }

    @Override
    public List<OutingGuide> analyze(List<Video> videos, BatchCompletedListener listener) {
        return analyze(null, videos, listener);
    }

    @Override
    public List<OutingGuide> analyze(Long syncId, List<Video> videos, BatchCompletedListener listener) {
        // 유효성 검사
        List<String> validatedVideoIds = YoutubeVideoValidator.validate(videos).stream()
                .map(Video::getId)
                .toList();

        Map<GuideKey, OutingGuide> analyzedResults = new ConcurrentHashMap<>();
        Map<Long, GuideKey> successfulAnalyses = new ConcurrentHashMap<>();

        // V2 호출은 syncId가 없어 호출 스레드별로 대기열을 나눈다. 음수라 실제 syncId와 겹치지 않는다. #263에서 사라진다.
        long queueId = syncId == null ? -Thread.currentThread().threadId() : syncId;
        BlockingQueue<BatchOutcome> completed = new LinkedBlockingQueue<>();
        List<CompletableFuture<?>> submitted = new ArrayList<>();

        for (int i = 0; i < validatedVideoIds.size(); i += 10) {
            List<String> batch = List.copyOf(validatedVideoIds.subList(i, Math.min(i + 10, validatedVideoIds.size())));
            AtomicReference<Map<String, Long>> analysisIds = new AtomicReference<>(Map.of());
            CompletableFuture<?> future = aiCallGateway
                    .submitAnalysis(queueId, batch, () -> analysisIds.set(startAnalyses(syncId, batch)))
                    .handle((response, error) -> completed.add(processBatch(
                            batch, analysisIds.get(), response, error, analyzedResults, successfulAnalyses, listener)));
            submitted.add(future);
        }

        try {
            Throwable failure = null;
            for (int i = 0; i < submitted.size(); i++) {
                failure = await(completed, failure);
            }
            if (failure != null) {
                throw new CompletionException(failure);
            }
        } finally {
            submitted.forEach(future -> future.cancel(false));
        }

        List<OutingGuide> analyzedGuides = analyzedResults.values().stream().toList();
        if (analyzedGuides.isEmpty()) {
            return List.of();
        }

        List<OutingGuide> persistedGuides = persistAnalyzedResults(analyzedGuides);
        if (!successfulAnalyses.isEmpty()) {
            videoAnalysisManager.completeSuccessful(successfulAnalyses, persistedGuides);
        }

        return persistedGuides;
    }

    private Map<String, Long> startAnalyses(Long syncId, List<String> videoIds) {
        Map<String, Long> analysisIds =
                syncId == null ? videoAnalysisManager.start(videoIds) : videoAnalysisManager.start(syncId, videoIds);
        Map<String, Long> analysisIdsByUrl = new HashMap<>();
        analysisIds.forEach((videoId, analysisId) -> analysisIdsByUrl.put(SHORTS_PREFIX + videoId, analysisId));
        return analysisIdsByUrl;
    }

    private BatchOutcome processBatch(
            List<String> videoIds,
            Map<String, Long> analysisIdsByUrl,
            AnalyzeVideoResponseV2 response,
            Throwable error,
            Map<GuideKey, OutingGuide> analyzedResults,
            Map<Long, GuideKey> successfulAnalyses,
            BatchCompletedListener listener) {
        Map<Long, VideoAnalysisStatus> unsuccessfulAnalyses = new HashMap<>();
        Set<Long> processedIds = new HashSet<>();

        // 실패 응답이나 응답 누락 시 FAILED로 처리한다.
        analysisIdsByUrl
                .values()
                .forEach(analysisId -> unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.FAILED));

        try {
            if (error != null) {
                log.error("AI 배치 분석 실패: videoCount={}", videoIds.size(), error);
                return new BatchOutcome(error);
            }

            if (response instanceof AnalyzeVideoResponseV2.Success success
                    && success.data() != null
                    && success.data().results() != null) {
                for (var result : success.data().results()) {
                    Long analysisId = analysisIdsByUrl.get(result.videoUrl());

                    if (analysisId == null || !processedIds.add(analysisId)) {
                        continue;
                    }

                    switch (result.status()) {
                        case "success" -> {
                            OutingGuide guide = OutingGuideFactory.createOutingGuide(result);
                            GuideKey key = GuideKey.from(guide);

                            analyzedResults.putIfAbsent(key, guide);
                            successfulAnalyses.put(analysisId, key);
                            unsuccessfulAnalyses.remove(analysisId);
                        }
                        case "not_found" -> unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.NOT_FOUND);
                        case "failed" -> unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.FAILED);
                        default -> throw new IllegalStateException("Unknown analysis status: " + result.status());
                    }
                }
            }
            return new BatchOutcome(null);
        } catch (RuntimeException processingError) {
            return new BatchOutcome(processingError);
        } finally {
            videoAnalysisManager.completeUnsuccessful(unsuccessfulAnalyses);
            listener.batchCompleted(videoIds.size(), unsuccessfulAnalyses.size());
        }
    }

    private Throwable await(BlockingQueue<BatchOutcome> completed, Throwable previous) {
        try {
            BatchOutcome outcome = completed.take();
            return previous == null ? outcome.error() : previous;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube 동기화 배치 대기가 중단되었습니다", error);
        }
    }

    private List<OutingGuide> persistAnalyzedResults(List<OutingGuide> analyzedResults) {
        Map<GuideKey, OutingGuide> allByAddresses =
                outingGuideRepository
                        .findAllByAddresses(analyzedResults.stream()
                                .map(OutingGuide::getAddress)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                GuideKey::from, outingGuide -> outingGuide, (existing, duplicate) -> existing));

        Map<GuideKey, OutingGuide> uniqueGuides = new LinkedHashMap<>();

        List<OutingGuide> persistedGuides = new ArrayList<>();
        List<OutingGuide> unembeddedGuides = new ArrayList<>();
        for (OutingGuide guide : analyzedResults) {
            GuideKey key = GuideKey.from(guide);
            if (!uniqueGuides.containsKey(key)) {
                uniqueGuides.put(key, guide);
                if (!allByAddresses.containsKey(key)) {
                    OutingGuide persistedGuide = outingGuideRepository.save(guide);
                    persistedGuides.add(persistedGuide);
                    unembeddedGuides.add(persistedGuide);
                } else {
                    persistedGuides.add(allByAddresses.get(key));
                }
            }
        }

        for (int i = 0; i < unembeddedGuides.size(); i += 100) {
            aiCallGateway
                    .submitEmbedding(unembeddedGuides.subList(i, Math.min(i + 100, unembeddedGuides.size())))
                    .join();
        }

        return persistedGuides;
    }

    private record BatchOutcome(Throwable error) {}
}
