package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.GuideKey;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoAnalysisManager {

    private final VideoAnalysisRepository videoAnalysisRepository;
    private final VideoAnalysisResultRepository videoAnalysisResultRepository;

    @Transactional
    public Map<String, Long> start(List<String> videoIds) {
        return start(null, videoIds);
    }

    @Transactional
    public Map<String, Long> start(Long youtubeSyncId, List<String> videoIds) {
        return videoAnalysisRepository
                .saveAll(videoIds.stream()
                        .distinct()
                        .map(videoId -> new VideoAnalysis(videoId, youtubeSyncId))
                        .toList())
                .stream()
                .collect(Collectors.toMap(VideoAnalysis::getVideoId, VideoAnalysis::getId));
    }

    @Transactional
    public void completeUnsuccessful(Map<Long, VideoAnalysisStatus> statuses) {
        for (VideoAnalysis analysis : videoAnalysisRepository.findAllById(statuses.keySet())) {
            switch (statuses.get(analysis.getId())) {
                case NOT_FOUND -> analysis.markNotFound();
                case FAILED -> analysis.markFailed();
                default -> throw new IllegalArgumentException("Unsupported analysis status");
            }
        }
    }

    @Transactional
    public void completeSuccessful(Map<Long, GuideKey> successfulAnalyses, Collection<OutingGuide> persistedGuides) {
        Map<GuideKey, OutingGuide> guidesByKey = persistedGuides.stream()
                .collect(Collectors.toMap(
                        guide -> new GuideKey(guide.getName(), guide.getAddress()), Function.identity()));

        List<VideoAnalysisResult> results = videoAnalysisRepository.findAllById(successfulAnalyses.keySet()).stream()
                .map(analysis -> {
                    OutingGuide guide = guidesByKey.get(successfulAnalyses.get(analysis.getId()));

                    if (guide == null || guide.getId() == null) {
                        throw new IllegalStateException("Persisted outing guide not found");
                    }

                    analysis.markSuccess();
                    return new VideoAnalysisResult(analysis, guide);
                })
                .toList();

        videoAnalysisResultRepository.saveAll(results);
    }
}
