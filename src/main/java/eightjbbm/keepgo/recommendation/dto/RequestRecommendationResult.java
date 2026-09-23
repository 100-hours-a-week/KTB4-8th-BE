package eightjbbm.keepgo.recommendation.dto;

import eightjbbm.keepgo.util.dto.RecommendCourseResponse;

import java.util.List;

public record RequestRecommendationResult(
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
    ) {
        public static RecommendationSnippet from(RecommendCourseResponse.RecommendData.RecommendCourse course) {
            return new RecommendationSnippet(
                    course.title(),
                    String.valueOf(100),
                    course.places().size(),
                    course.places()
                            .stream()
                            .mapToInt(RecommendCourseResponse.RecommendData.RecommendCourse.RecommendPlace::travelMinutes)
                            .sum()
            );
        }
    }

    public record RecommendationDetail(
            String title,
            String recommendationScore,
            Integer totalTravelTime,
            List<RecommendationDetailItem> items
    ) {
        public static RecommendationDetail from(RecommendCourseResponse.RecommendData.RecommendCourse course) {
            return new RecommendationDetail(
                    course.title(),
                    String.valueOf(100),
                    course.places().stream().mapToInt(RecommendCourseResponse.RecommendData.RecommendCourse.RecommendPlace::travelMinutes).sum(),
                    course.places().stream().map(RecommendationDetailItem::from).toList()
            );
        }
    }

    public record RecommendationDetailItem(
            Integer sequence,
            String name,
            String category,
            String description,
            Float lat,
            Float lng
    ) {
        public static RecommendationDetailItem from(RecommendCourseResponse.RecommendData.RecommendCourse.RecommendPlace place) {
            return new RecommendationDetailItem(
                    null,
                    place.placeName(),
                    null,
                    place.travelMinutes().toString(),
                    null,
                    null
            );
        }
    }

    public static RequestRecommendationResult from(
            RequestRecommendationCommand command,
            RecommendCourseResponse response) {
        return new RequestRecommendationResult(
                new RecommendationMetadata(
                        response.data().courses().size(),
                        command.location(),
                        command.requestedTime().toString()
                ),
                response.data().courses().stream().map(RecommendationSnippet::from).toList(),
                response.data().courses().stream().map().toList()
        );
    }
}
