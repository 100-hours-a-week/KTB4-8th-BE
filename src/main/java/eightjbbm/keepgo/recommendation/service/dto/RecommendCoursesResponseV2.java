package eightjbbm.keepgo.recommendation.service.dto;

import java.time.LocalTime;
import java.util.List;

public interface RecommendCoursesResponseV2 {
    record Success(
            String message,
            SuccessData data
    ) implements RecommendCoursesResponseV2 {}

    record SuccessData(
            Object error,
            Integer matchScore,
            List<Course> courses
    ) {}

    record Course(
            String title,
            List<Place> places,
            Integer totalDurationMinutes
    ) {}

    record Place(
            String placeId,
            String placeName,
            Integer travelMinutes,
            LocalTime arrivalTime,
            Integer stayMinutes,
            String reason
    ) {}

    record Failure(
        String message,
        Object data
    ) implements RecommendCoursesResponseV2 {}
}
