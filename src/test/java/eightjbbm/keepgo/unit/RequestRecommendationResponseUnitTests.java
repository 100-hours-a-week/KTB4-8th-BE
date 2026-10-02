package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResponse;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData.RecommendCourse;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse.RecommendData.RecommendCourse.RecommendPlace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// 코스 추천 응답 단위 테스트
///
/// RequestRecommendationResponse.from()이 null을 반환해 추천 결과가 항상 빈 본문으로 나가던 문제(2026-10-03)의 회귀 테스트
public class RequestRecommendationResponseUnitTests {

    /// application.yaml 의 spring.jackson.property-naming-strategy: SNAKE_CASE 와 같은 설정
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    @DisplayName("""
            추천 결과를 FE가 읽는 형식(metadata·details, snake_case)으로 내보낸다
            """)
    void serializeForFrontend() {
        SlotValue slot = SlotValue.from(null, "서울 강남구", LocalDateTime.of(2026, 10, 3, 15, 0), 180, List.of("카페"));
        RecommendCourseResponse ai = new RecommendCourseResponse("ok", new RecommendData(List.of(
                new RecommendCourse("강남 조용한 코스", List.of(
                        new RecommendPlace("web-0-0", "카페 A", 15, "15:15", 60, "조용함"),
                        new RecommendPlace("web-0-1", "전시 B", 10, "16:25", 60, "한적함")
                ), 145)
        )));

        var result = RequestRecommendationResult.from(slot, ai, place -> null);
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
    @DisplayName("""
            코스가 없으면 빈 목록으로 내보낸다
            """)
    void emptyCourses() {
        SlotValue slot = SlotValue.from(null, null, null, null, null);
        var result = RequestRecommendationResult.from(slot, new RecommendCourseResponse("ok", new RecommendData(List.of())), place -> null);

        JsonNode json = MAPPER.valueToTree(RequestRecommendationResponse.from(result));

        assertThat(json.get("metadata").get("total_course_count").asInt()).isZero();
        assertThat(json.get("details")).isEmpty();
    }
}
