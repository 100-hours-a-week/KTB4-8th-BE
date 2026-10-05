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
                .exchange((_, res) -> {
                    log.info(res.getStatusCode().toString());
                   int status = res.getStatusCode().value();

                   return switch (status) {
                       case 200 -> res.bodyTo(RecommendCourseResponse.Success.class);
                       case 400, 422, 429, 500, 502, 504 -> res.bodyTo(RecommendCourseResponse.Error.class);
                       default -> {
                           throw new IllegalStateException("처리하지 못한 응답 코드: " + status);
                       }
                   };
                });
        log.info(response.toString());
        return response;
    }

    public void stopRecommendation(Long jobId) {
        aiServerRestClient.post()
                .uri("/v1/recommend-courses/{jobId}/cancel", jobId)
                .retrieve()
                .body(Void.class);
    }

    public ExtractSlotResponse extractSlot(
            ExtractSlotRequest request
    ) {
        var response = aiServerRestClient.post()
                .uri("/v1/extract")
                .body(request)
                .exchange((_, res) -> {
                    log.info(res.getStatusCode().toString());
                    int status = res.getStatusCode().value();

                    return switch (status) {
                        case 200 -> res.bodyTo(ExtractSlotResponse.Success.class);
                        case 400, 422, 429, 500, 502, 504 -> res.bodyTo(ExtractSlotResponse.Error.class);
                        default -> {
                            throw new IllegalStateException("처리하지 못한 응답 코드: " + status);
                        }
                    };
                });
        log.info(response.toString());
        return response;
    }

    public AnalyzeVideoResponse analyzeVideo(AnalyzeVideoRequest request) {
        var response = aiServerRestClient.post()
                .uri("/v1/analyze-video")
                .body(request)
                .exchange((_, res) -> {
                    log.info(res.getStatusCode().toString());
                    int status = res.getStatusCode().value();

                    return switch (status) {
                        case 200 -> res.bodyTo(AnalyzeVideoResponse.Success.class);
                        case 400, 422, 429, 500, 502, 504 -> res.bodyTo(AnalyzeVideoResponse.Error.class);
                        default -> {
                            throw new IllegalStateException("처리하지 못한 응답 코드: " + status);
                        }
                    };
                });
        log.info(response.toString());
        return response;
    }
}
