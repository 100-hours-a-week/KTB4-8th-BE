package eightjbbm.keepgo.util;

import eightjbbm.keepgo.util.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final String baseUrl = "";
    private final RestClient aiServerRestClient;

    public RecommendCourseResponse recommendCourse(RecommendCourseRequest request) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .host(baseUrl)
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
                                    .host(baseUrl)
                                    .path("/v1/recommend-course/" + jobId + "/cancel");
                            return builder.build();
                        }
                ).retrieve().body(Void.class);
    }

    public ExtractSlotResponse extractSlot(ExtractSlotRequest request) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .host(baseUrl)
                                    .path("/v1/extract");
                            return builder.build();
                        }
                )
                .body(request)
                .retrieve()
                .body(ExtractSlotResponse.class);
    }

    public AnalyzeVideoResponse analyzeVideo(String url) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .host(baseUrl)
                                    .path("/v1/analyze-video");
                            return builder.build();
                        }
                )
                .body(url)
                .retrieve()
                .body(AnalyzeVideoResponse.class);
    }
}
