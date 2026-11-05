package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;

public interface YoutubeVideoAnalyzer {

    List<OutingGuide> analyze(List<Video> videos);

    /// 배치 하나를 처리할 때마다 콜백으로 처리·실패 영상 수를 알린다.
    List<OutingGuide> analyze(List<Video> videos, BatchCompletedListener listener);

    /// 분석 이력을 동기화와 연결한다. 연결할 동기화가 없으면 그대로 위임한다.
    default List<OutingGuide> analyze(Long syncId, List<Video> videos, BatchCompletedListener listener) {
        return analyze(videos, listener);
    }

    @FunctionalInterface
    interface BatchCompletedListener {
        void batchCompleted(int processedCount, int failedCount);
    }
}
