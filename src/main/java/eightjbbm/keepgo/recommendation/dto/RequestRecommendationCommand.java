package eightjbbm.keepgo.recommendation.dto;

import java.time.Instant;

/// @param userId 회원 ID
/// @param availableTime 가능한 시간대
/// @param category 카테고리
/// @param originLat 사용자의 위도
/// @param originLng 사용자의 경도
/// @param requestedTime 희망 일자
/// @param location 희망 지역
public record RequestRecommendationCommand(
        Long userId,
        Integer availableTime,
        String category,
        Float originLat,
        Float originLng,
        Instant requestedTime,
        String location
) {
}
