package eightjbbm.keepgo.recommendation.dto;

import java.util.List;

public record RequestRecommendationResponse(
        RecommendationMetadata metadata,
        List<RecommendationSnippet> snippets,
        List<RecommendationDetail> details
) {
    public record RecommendationMetadata(
            Integer totalCourseCount,
            String location,
            String scheduledTime
    ) {}

    public record RecommendationSnippet(
            String title,
            String recommendationScore,
            Integer totalPlaceCount,
            Integer totalTravelTime
    ) {}

    public record RecommendationDetail(
            String title,
            String recommendationScore,
            Integer totalTravelTime,
            List<RecommendationDetailItem> items
    ) {}

    public record RecommendationDetailItem(
            Integer sequence,
            String name,
            String category,
            String description,
            Float lat,
            Float lng
    ) {}

    public static RequestRecommendationResponse from(RequestRecommendationResult result) {
        return null;
    }
}
