package eightjbbm.keepgo.recommendation.dto;

/// @param userId 회원 ID
public record RequestRecommendationCommand(
        Long userId
) {
}
