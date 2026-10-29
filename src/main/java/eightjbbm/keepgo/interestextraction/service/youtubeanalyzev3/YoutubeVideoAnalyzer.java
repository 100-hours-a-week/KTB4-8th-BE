package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface YoutubeVideoAnalyzer {

    List<OutingGuide> analyze(List<Video> videos);
}
