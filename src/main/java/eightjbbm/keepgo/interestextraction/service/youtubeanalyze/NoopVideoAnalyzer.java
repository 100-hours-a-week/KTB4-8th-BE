package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NoopVideoAnalyzer {
    public Map<Video, OutingGuide> analyzeVideosInternalByBackend(List<Video> videos) {
        return Map.of();
    }
}
