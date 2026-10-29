package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;

public interface YoutubeVideoAnalyzer {

    List<OutingGuide> analyze(List<Video> videos);

    /// 배치 하나를 처리할 때마다 콜백으로 처리·실패 영상 수를 알린다.
    List<OutingGuide> analyze(List<Video> videos, BatchCompletedListener listener);

    @FunctionalInterface
    interface BatchCompletedListener {
        void batchCompleted(int processedCount, int failedCount);
    }
}
