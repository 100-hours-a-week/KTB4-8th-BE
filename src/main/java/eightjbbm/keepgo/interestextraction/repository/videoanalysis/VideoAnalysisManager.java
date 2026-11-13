package eightjbbm.keepgo.interestextraction.repository.videoanalysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoAnalysisManager {

    private final VideoAnalysisRepository videoAnalysisRepository;

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
    public void failInProgress(Long youtubeSyncId) {
        videoAnalysisRepository.markFailedBySyncIds(List.of(youtubeSyncId), Instant.now());
    }
}
