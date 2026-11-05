package eightjbbm.keepgo.unit;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResponse;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationDetail;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationDetailItem;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationMetadata;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult.RecommendationSnippet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("코스 추천 응답 변환")
class RequestRecommendationResponseTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    @DisplayName("추천 결과를 프런트엔드의 snake_case 형식으로 내보낸다")
    void serializeForFrontend() {
        var result = new RequestRecommendationResult(
                new RecommendationMetadata(1, "서울 강남구", "2026-10-03T15:00"),
                List.of(new RecommendationSnippet("강남 조용한 코스", "100", 2, 25)),
                List.of(new RecommendationDetail(
                        "강남 조용한 코스",
                        "100",
                        25,
                        List.of(
                                new RecommendationDetailItem(0, "카페 A", "카페", "15", 37.5f, 127.0f),
                                new RecommendationDetailItem(1, "전시 B", "전시", "10", 37.6f, 127.1f)))));

        JsonNode json = MAPPER.valueToTree(RequestRecommendationResponse.from(result));

        assertThat(json.get("metadata").get("total_course_count").asInt()).isEqualTo(1);
        assertThat(json.get("metadata").get("location").asString()).isEqualTo("서울 강남구");
        assertThat(json.get("metadata").get("scheduled_time").asString()).isEqualTo("2026-10-03T15:00");
        JsonNode detail = json.get("details").get(0);
        assertThat(detail.get("title").asString()).isEqualTo("강남 조용한 코스");
        assertThat(detail.get("total_travel_time").asInt()).isEqualTo(25);
        assertThat(detail.get("items")).hasSize(2);
        assertThat(detail.get("items").get(0).get("name").asString()).isEqualTo("카페 A");
        assertThat(detail.get("items").get(0).get("description").asString()).isEqualTo("15");
        assertThat(json.get("snippets").get(0).get("total_place_count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("코스가 없으면 빈 목록으로 내보낸다")
    void emptyCourses() {
        var result = new RequestRecommendationResult(new RecommendationMetadata(0, null, null), List.of(), List.of());

        JsonNode json = MAPPER.valueToTree(RequestRecommendationResponse.from(result));

        assertThat(json.get("metadata").get("total_course_count").asInt()).isZero();
        assertThat(json.get("details")).isEmpty();
        assertThat(json.get("snippets")).isEmpty();
    }
}
