package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface YoutubeVideoAnalyzer {

    List<OutingGuide> analyze(List<Video> videos);
}

