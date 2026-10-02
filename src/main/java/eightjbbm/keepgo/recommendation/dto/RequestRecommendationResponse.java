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

    /// 서비스 결과를 그대로 옮긴다. FE(lib/api/backend.ts toRecommendationRun)가 metadata·details를 읽는다.
    public static RequestRecommendationResponse from(RequestRecommendationResult result) {
        var metadata = result.metadata();
        return new RequestRecommendationResponse(
                new RecommendationMetadata(
                        metadata.totalCourseCount(),
                        metadata.location(),
                        metadata.scheduledTime()
                ),
                result.snippets().stream()
                        .map(s -> new RecommendationSnippet(
                                s.title(), s.recommendationScore(), s.totalPlaceCount(), s.totalTravelTime()
                        ))
                        .toList(),
                result.details().stream()
                        .map(d -> new RecommendationDetail(
                                d.title(),
                                d.recommendationScore(),
                                d.totalTravelTime(),
                                d.items().stream()
                                        .map(i -> new RecommendationDetailItem(
                                                i.sequence(), i.name(), i.category(), i.description(), i.lat(), i.lng()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }
}
