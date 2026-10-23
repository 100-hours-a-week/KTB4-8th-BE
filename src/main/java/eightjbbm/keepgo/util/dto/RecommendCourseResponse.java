package eightjbbm.keepgo.util.dto;

import java.util.List;

public sealed interface RecommendCourseResponse {
    public record Success(
            String message,
            RecommendData data
    ) implements RecommendCourseResponse {}

    public record RecommendData(
            List<RecommendCourse> courses
    ) {}

    public record RecommendCourse(
            String title,
            List<RecommendPlace> places,
            Integer totalDurationMinutes
    ) {}

    public record RecommendPlace(
            String placeId,
            String placeName,
            Integer travelMinutes,
            String arrivalTime,
            Integer stayMinutes,
            String reason
    ) {}

    record Error(
            String message,
            Object data
    ) implements RecommendCourseResponse {}
}
