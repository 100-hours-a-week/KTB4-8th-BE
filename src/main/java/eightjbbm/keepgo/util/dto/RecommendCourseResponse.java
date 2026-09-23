package eightjbbm.keepgo.util.dto;

import java.util.List;

public record RecommendCourseResponse(
        String message,
        RecommendData data
) {
    public record RecommendData(
            List<RecommendCourse> courses
    ) {
        public record RecommendCourse(
                String title,
                List<RecommendPlace> places,
                Integer totalDurationMinutes
        ) {
            public record RecommendPlace(
                    String placeId,
                    String placeName,
                    Integer travelMinutes,
                    String arrivalTime,
                    Integer stayMinutes,
                    String reason
            ) {}
        }
    }
}
