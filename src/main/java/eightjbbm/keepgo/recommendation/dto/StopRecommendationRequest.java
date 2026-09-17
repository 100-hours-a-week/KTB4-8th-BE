package eightjbbm.keepgo.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record StopRecommendationRequest(
        @NotBlank Long recommendationId
) {
}
