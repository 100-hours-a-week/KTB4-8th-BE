package eightjbbm.keepgo.recommendation.service.dto;

import jakarta.annotation.Nullable;
import java.time.LocalTime;
import java.util.List;

public interface RecommendCoursesResponseV2 {
    /// `done` 이벤트 본문. `message`로 업무 결과를 판정한다.
    record Done(String message, @Nullable SuccessData data) {}

    record SuccessData(@Nullable String error, @Nullable List<Course> courses) {}

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
