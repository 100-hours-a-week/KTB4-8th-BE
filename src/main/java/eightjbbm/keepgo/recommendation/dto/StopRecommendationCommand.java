package eightjbbm.keepgo.recommendation.dto;

public record StopRecommendationCommand(
        Long userId,
        Long recommendationId
) {
}
