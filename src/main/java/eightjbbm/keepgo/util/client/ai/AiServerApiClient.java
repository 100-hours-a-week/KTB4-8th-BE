package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class AiServerApiClient {
    @Qualifier("aiServerRestClient")
    private final RestClient aiServerRestClient;

    public RecommendCourseResponse recommendCourse(RecommendCourseRequest request) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/v1/recommend-courses");
                            return builder.build();
                        }
                )
                .body(request)
                .retrieve()
                .body(RecommendCourseResponse.class);
    }

    public void stopRecommendation(Long jobId) {
        aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/v1/recommend-courses/" + jobId + "/cancel");
                            return builder.build();
                        }
                ).retrieve().body(Void.class);
    }

    public ExtractSlotResponse extractSlot(ExtractSlotRequest request) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/v1/extract");
                            return builder.build();
                        }
                )
                .body(request)
                .retrieve()
                .body(ExtractSlotResponse.class);
    }

    public AnalyzeVideoResponse analyzeVideo(AnalyzeVideoRequest request) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/v1/analyze-video");
                            return builder.build();
                        }
                )
                .body(request)
                .retrieve()
                .body(AnalyzeVideoResponse.class);
    }
}
