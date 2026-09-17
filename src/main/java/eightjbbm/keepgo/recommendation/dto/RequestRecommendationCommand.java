package eightjbbm.keepgo.recommendation.dto;

import java.time.Instant;

public record RequestRecommendationCommand(
        Long userId,
        String availableTime,
        String category,
        Double originLat,
        Double originLng,
        Instant requestedTime
) {
}
