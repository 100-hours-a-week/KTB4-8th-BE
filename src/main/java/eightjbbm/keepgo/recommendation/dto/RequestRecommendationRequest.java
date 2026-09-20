package eightjbbm.keepgo.recommendation.dto;

/// @param lat
/// @param lng
/// @param location
/// @param scheduledTimeSlot
/// @param availableTimeDuration
/// @param category
public record RequestRecommendationRequest(
        Float lat,
        Float lng,
        String location,
        String scheduledTimeSlot,
        String availableTimeDuration,
        String category
) {
}
