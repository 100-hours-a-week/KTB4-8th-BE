package eightjbbm.keepgo.recommendation.service.dto;

import java.util.List;

public record RecommendOutingCourseResultV2(
        OutingCourseMetadata metadata,
        List<OutingCourseSnippet> snippets,
        List<OutingCourseDetail> details
) {
    public record OutingCourseMetadata(
            Integer totalCourseCount,
            String location,
            String scheduledTime
    ) {}

    public record OutingCourseSnippet(
            String title,
            String recommendationScore,
            Integer totalPlaceCount,
            Integer totalTravelTime
    ) {}

    public record OutingCourseDetail(
            Long courseId,
            String title,
            String recommendationScore,
            Integer totalTravelTime,
            List<OutingCourseStop> items
    ) {}

    public record OutingCourseStop(
            Integer sequence,
            String name,
            String category,
            Float lat,
            Float lng
    ) {}
}
