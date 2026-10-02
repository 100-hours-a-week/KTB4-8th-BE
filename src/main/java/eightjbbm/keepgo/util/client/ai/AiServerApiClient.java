package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerApiClient {
    @Qualifier("aiServerRestClient")
    private final RestClient aiServerRestClient;

    public RecommendCourseResponse recommendCourse(RecommendCourseRequest request) {
        var response = aiServerRestClient.post()
                .uri("/v1/recommend-courses")
                .body(request)
                .retrieve()
                .body(RecommendCourseResponse.class);
        log.info(response.toString());
        return response;
    }

    public void stopRecommendation(Long jobId) {
        aiServerRestClient.post()
                .uri("/v1/recommend-courses/{jobId}/cancel", jobId)
                .retrieve()
                .body(Void.class);
    }

    public ExtractSlotResponse extractSlot(ExtractSlotRequest request) {
        var response = aiServerRestClient.post()
                .uri("/v1/extract")
                .body(request)
                .retrieve()
                .body(ExtractSlotResponse.class);
        log.info(response.toString());
        return response;
    }

    public AnalyzeVideoResponse analyzeVideo(AnalyzeVideoRequest request) {
        var response = aiServerRestClient.post()
                .uri("/v1/analyze-video")
                .body(request)
                .retrieve()
                .body(AnalyzeVideoResponse.class);
        log.info(response.toString());
        return response;
    }
}
