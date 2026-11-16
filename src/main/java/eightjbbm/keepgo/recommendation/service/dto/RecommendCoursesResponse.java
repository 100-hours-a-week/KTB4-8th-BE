package eightjbbm.keepgo.recommendation.service.dto;

import jakarta.annotation.Nullable;
import java.time.LocalTime;
import java.util.List;

public interface RecommendCoursesResponse {
    /// `done` 이벤트 본문. `message`로 업무 결과를 판정한다.
    record Done(
            String message,
            @Nullable String error,
            @Nullable List<Course> courses) {}

    record SuccessData(@Nullable String error, @Nullable List<Course> courses) {
        public static SuccessData from(Done response) {
            return new SuccessData(response.error(), response.courses());
        }
    }

    record Course(
            String title,
            List<Place> places,
            Integer totalDurationMinutes,
            @Nullable Integer matchScore) {}

    record Place(
            String placeId,
            String placeName,
            Integer travelMinutes,
            LocalTime arrivalTime,
            Integer stayMinutes,
            String reason) {}
}
