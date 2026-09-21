package eightjbbm.keepgo.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendCourseRequest(
        String query,
        List<RecommendCandidate> candidates,
        List<RecommendHistoryPlaceId> historyPlaceIds,
        Integer availableTime,
        String category,
        LocalDateTime dateTime,
        RecommendOrigin origin
) {
    public record RecommendCandidate(
            String placeId,
            String placeName,
            String summary,
            Float lat,
            Float lng,
            String businessHours
    ) {}

    public record RecommendHistoryPlaceId(
            String placeId,
            LocalDate savedAt
    ) {}

    public record RecommendOrigin(
            Float lat,
            Float lng
    ) {}
}
