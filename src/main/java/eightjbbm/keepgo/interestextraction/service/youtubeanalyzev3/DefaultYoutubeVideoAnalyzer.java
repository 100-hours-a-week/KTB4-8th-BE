package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.GuideKey;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.OutingGuideFactory;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import eightjbbm.keepgo.sandbox.aiv2.AnalyzeVideoResponseV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultYoutubeVideoAnalyzer implements YoutubeVideoAnalyzer {

    private final AiServerService aiServerApiService;
    private final OutingGuideRepository outingGuideRepository;
    private final VideoAnalysisManager videoAnalysisManager;

    @Override
    public List<OutingGuide> analyze(List<Video> videos) {
        // 유효성 검사
        List<String> validatedVideoIds = YoutubeVideoValidator.validate(videos).stream().map(Video::getId).toList();

        Map<GuideKey, OutingGuide> analyzedResults = new ConcurrentHashMap<>();
        Map<Long, GuideKey> successfulAnalyses = new ConcurrentHashMap<>();

        try (var executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();

            for (int i = 0; i < validatedVideoIds.size(); i += 10) {
                List<String> batch = validatedVideoIds.subList(
                        i, Math.min(i + 10, validatedVideoIds.size()));

                tasks.add(CompletableFuture.runAsync(
                        () -> analyzeBatch(batch, analyzedResults, successfulAnalyses),
                        executor));
            }

            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }

        if (!successfulAnalyses.isEmpty()) {
            List<OutingGuide> persistedGuides =
                    persistAnalyzedResults(analyzedResults.values().stream().toList());

            videoAnalysisManager.completeSuccessful(successfulAnalyses, persistedGuides);
        }

        // 기존 return 유지. 위에서 저장한 가이드는 기존 조회 로직에서 재사용된다.
        return persistAnalyzedResults(analyzedResults.values().stream().toList());
    }


    private void analyzeBatch(
            List<String> videoIds,
            Map<GuideKey, OutingGuide> analyzedResults,
            Map<Long, GuideKey> successfulAnalyses
    ) {
        Map<String, Long> analysisIdsByUrl = new HashMap<>();
        videoAnalysisManager.start(videoIds).forEach(
                (videoId, analysisId) ->
                        analysisIdsByUrl.put("https://youtube.com/shorts/" + videoId, analysisId));

        Map<Long, VideoAnalysisStatus> unsuccessfulAnalyses = new HashMap<>();
        Set<Long> processedIds = new HashSet<>();

        // 실패 응답이나 응답 누락 시 FAILED로 처리한다.
        analysisIdsByUrl.values().forEach(
                analysisId -> unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.FAILED));

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
                            case "not_found" ->
                                unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.NOT_FOUND);
                            case "failed" ->
                                unsuccessfulAnalyses.put(analysisId, VideoAnalysisStatus.FAILED);
                            default -> throw new IllegalStateException(
                                "Unknown analysis status: " + result.status());
                        }
                    }
                }
        } finally {
                videoAnalysisManager.completeUnsuccessful(unsuccessfulAnalyses);
        }
    }

    private List<OutingGuide> persistAnalyzedResults(List<OutingGuide> analyzedResults) {
        Map<GuideKey, OutingGuide> allByAddresses = outingGuideRepository
                .findAllByAddresses(
                        analyzedResults.stream()
                                .map(OutingGuide::getAddress)
                                .distinct()
                                .toList()).stream()
                .collect(Collectors.toMap(
                        outingGuide -> new GuideKey(outingGuide.getName(), outingGuide.getAddress()),
                        outingGuide -> outingGuide,
                        (existing, duplicate) -> existing));

        Map<GuideKey, OutingGuide> uniqueGuides = new LinkedHashMap<>();

        List<OutingGuide> persistedGuides = new ArrayList<>();
        List<OutingGuide> unembeddedGuides = new ArrayList<>();
        for (OutingGuide guide: analyzedResults) {
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
