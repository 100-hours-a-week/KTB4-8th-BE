package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultYoutubeVideoAnalyzer implements YoutubeVideoAnalyzer {

    private final AiServerService aiServerApiService;
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

        try (var executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();

            for (int i = 0; i < validatedVideoIds.size(); i += 10) {
                List<String> batch = validatedVideoIds.subList(i, Math.min(i + 10, validatedVideoIds.size()));

                tasks.add(CompletableFuture.runAsync(
                        () -> analyzeBatch(syncId, batch, analyzedResults, successfulAnalyses, listener), executor));
            }

            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
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

    private void analyzeBatch(
            Long syncId,
            List<String> videoIds,
            Map<GuideKey, OutingGuide> analyzedResults,
            Map<Long, GuideKey> successfulAnalyses,
            BatchCompletedListener listener) {
        Map<String, Long> analysisIdsByUrl = new HashMap<>();
        Map<String, Long> analysisIds =
                syncId == null ? videoAnalysisManager.start(videoIds) : videoAnalysisManager.start(syncId, videoIds);
        analysisIds.forEach(
                (videoId, analysisId) -> analysisIdsByUrl.put("https://youtube.com/shorts/" + videoId, analysisId));

        Map<Long, VideoAnalysisStatus> unsuccessfulAnalyses = new HashMap<>();
        Set<Long> processedIds = new HashSet<>();

        // 실패 응답이나 응답 누락 시 FAILED로 처리한다.
        analysisIdsByUrl
                .values()
                .forEach(analysisId -> unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.FAILED));

        try {
            var response = aiServerApiService.analyzeVideoV2(videoIds);

            if (response instanceof AnalyzeVideoResponseV2.Success success) {
                for (var result : success.data().results()) {
                    Long analysisId = analysisIdsByUrl.get(result.videoUrl());

                    if (analysisId == null || !processedIds.add(analysisId)) {
                        continue;
                    }

                    switch (result.status()) {
                        case "success" -> {
                            OutingGuide guide = OutingGuideFactory.createOutingGuide(result);
                            GuideKey key = new GuideKey(guide.getName(), guide.getAddress());

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
        } finally {
            videoAnalysisManager.completeUnsuccessful(unsuccessfulAnalyses);
            listener.batchCompleted(videoIds.size(), unsuccessfulAnalyses.size());
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
                                outingGuide -> new GuideKey(outingGuide.getName(), outingGuide.getAddress()),
                                outingGuide -> outingGuide,
                                (existing, duplicate) -> existing));

        Map<GuideKey, OutingGuide> uniqueGuides = new LinkedHashMap<>();

        List<OutingGuide> persistedGuides = new ArrayList<>();
        List<OutingGuide> unembeddedGuides = new ArrayList<>();
        for (OutingGuide guide : analyzedResults) {
            GuideKey key = new GuideKey(guide.getName(), guide.getAddress());
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
            aiServerApiService.embedPlaces(unembeddedGuides.subList(i, Math.min(i + 100, unembeddedGuides.size())));
        }

        return persistedGuides;
    }
}
