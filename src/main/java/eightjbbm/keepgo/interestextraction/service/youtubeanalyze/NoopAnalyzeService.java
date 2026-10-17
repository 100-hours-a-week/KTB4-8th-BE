package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;

import java.util.List;
import java.util.Map;

public class NoopAnalyzeService implements YoutubeAnalyzeService {
    @Override
    public Map<Video, OutingGuide> analyzeVideosInternalByBackend(List<Video> videos) {
        return Map.of();
    }
}
