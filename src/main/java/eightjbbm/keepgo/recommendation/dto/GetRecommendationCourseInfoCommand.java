package eightjbbm.keepgo.recommendation.dto;

public record GetRecommendationCourseInfoCommand(
        Long userId,
        Long recommendationId
) {
}
