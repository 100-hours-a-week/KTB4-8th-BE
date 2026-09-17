package eightjbbm.keepgo.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record RequestRecommendationRequest(
        @NotBlank String availableTime,
        @NotBlank String category,
        @NotBlank Double originLat,
        @NotBlank Double originLng,
        @NotBlank Instant requestedTime
) {
}
