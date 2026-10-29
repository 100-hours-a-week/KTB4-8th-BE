package eightjbbm.perf;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoStatus;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeConnectionChecker;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// k6 측정?�서 YouTube ?��? ?�출�??�동 ?�인??고정 조건?�로 ?�체한??
@Configuration(proxyBeanMethods = false)
public class PerfHarnessConfiguration {

    @Bean
    @Primary
    YoutubeVideoProvider perfYoutubeVideoProvider() {
        int videoCount = Integer.parseInt(environment("PERF_VIDEO_COUNT", "30"));
        int pageSize = Integer.parseInt(environment("PERF_VIDEO_PAGE_SIZE", "50"));
        long delayMillis = Long.parseLong(environment("PERF_YOUTUBE_DELAY_MS", "200"));
        return new FixedYoutubeVideoProvider(videoCount, pageSize, delayMillis);
    }

    @Bean
    @Primary
    YoutubeConnectionChecker perfYoutubeConnectionChecker() {
        return memberId -> {};
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /// ?�원마다 ?�로 ?�른 ?�상 ID�??�려�?�??�기?�에?�도 ?�원 �?가?�드 ?�성??겹치지 ?�게 ?�다.
    static final class FixedYoutubeVideoProvider implements YoutubeVideoProvider {

        private final int videoCount;
        private final int pageSize;
        private final long delayMillis;

        FixedYoutubeVideoProvider(int videoCount, int pageSize, long delayMillis) {
            this.videoCount = videoCount;
            this.pageSize = pageSize;
            this.delayMillis = delayMillis;
        }

        @Override
        public VideoListResponse getVideoLists(Long memberId, String pageToken) {
            sleep();
            int page = pageToken == null ? 0 : Integer.parseInt(pageToken);
            int from = page * pageSize;
            int to = Math.min(videoCount, from + pageSize);
            List<Video> videos = new ArrayList<>();
            for (int index = from; index < to; index++) {
                videos.add(video("m" + memberId + "-v" + index));
            }

            VideoListResponse response = new VideoListResponse();
            response.setItems(videos);
            if (to < videoCount) {
                response.setNextPageToken(String.valueOf(page + 1));
            }
            return response;
        }

        private void sleep() {
            if (delayMillis <= 0) {
                return;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private static Video video(String id) {
            VideoStatus status = new VideoStatus();
            status.setUploadStatus("uploaded");
            status.setPrivacyStatus("public");

            VideoContentDetails details = new VideoContentDetails();
            details.setDuration("PT1M");

            Video video = new Video();
            video.setId(id);
            video.setStatus(status);
            video.setContentDetails(details);
            return video;
        }
    }
}
