package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;

public interface YoutubeVideoAnalyzer {

    List<OutingGuide> analyze(List<Video> videos);
}
