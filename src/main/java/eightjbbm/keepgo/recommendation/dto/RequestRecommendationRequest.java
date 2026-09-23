package eightjbbm.keepgo.recommendation.dto;

import java.time.Instant;

/// @param lat 사용자의 위도
/// @param lng 사용자의 경도
/// @param location 희망하는 지역
/// @param scheduledTimeSlot 희망하는 날짜/시간대
/// @param availableTime 외출 가능 시간
/// @param category 카테고리
public record RequestRecommendationRequest(
        Float lat,
        Float lng,
        String location,
        Instant scheduledTimeSlot,
        Integer availableTime,
        String category
) {
}
