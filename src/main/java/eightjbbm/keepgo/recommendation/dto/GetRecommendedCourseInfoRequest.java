package eightjbbm.keepgo.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record GetRecommendedCourseInfoRequest(
        @NotBlank Long recommendationId
) {
}
