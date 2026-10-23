package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesRequestV2;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class AiServerRecommendApiService {

    private final RestClient restClient;

    public AiServerRecommendApiService(
            @Qualifier("aiServerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public RecommendCoursesResponseV2 recommendCourses(
            OutingCriteria outingCriteria,
            List<OutingGuide> outingGuides
    ) {
        return restClient.post()
                .uri("/v1/recommend-courses")
                .body(RecommendCoursesRequestV2.from(outingCriteria, outingGuides))
                .exchange((_, res) -> {
                    int status = res.getStatusCode().value();
                    return switch (status) {
                        case 200 -> res.bodyTo(RecommendCoursesResponseV2.Success.class);
                        case 400, 422, 429, 500, 502, 504 -> res.bodyTo(RecommendCoursesResponseV2.Failure.class);
                        default -> throw new RuntimeException();
                    };
                });
    }
}
