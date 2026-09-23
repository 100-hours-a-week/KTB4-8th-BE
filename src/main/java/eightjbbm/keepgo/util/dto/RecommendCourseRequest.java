package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/// @param query {@code String} 사용자 채팅의 쿼리 (짬통 키워드)
/// @param candidates {@code List<RecommendCandidate>} 장소 후보 (거리 기반으로 1차 필터링이 된 상태)
/// @param historyPlaceIds {@code List<RecommendHistoryPlaceId>} 장소 후보가 등록된 일자
/// @param availableTime {@code Integer} 가능한 시간대 (분 단위)
/// @param category {@code String} 장소의 카테고리
/// @param dateTime {@code LocalDateTime} 희망 일자
/// @param origin {@code RecommendOrigin} 사용자의 원래 위치
public record RecommendCourseRequest(
        String query,
        List<RecommendCandidate> candidates,
        List<RecommendHistoryPlaceId> historyPlaceIds,
        Integer availableTime,
        String category,
        LocalDateTime dateTime,
        RecommendOrigin origin
) {
    public record RecommendCandidate(
            String placeId,
            String placeName,
            String summary,
            Float lat,
            Float lng,
            String businessHours
    ) {
        public static RecommendCandidate from(OutingGuide place) {
            return new RecommendCandidate(
                    place.getId().toString(),
                    place.getName(),
                    place.getDescription(),
                    null,
                    null,
                    place.getBusinessHours()
            );
        }
    }

    public record RecommendHistoryPlaceId(
            String placeId,
            LocalDate savedAt
    ) {
        public static RecommendHistoryPlaceId from(OutingCollectionPrivate place) {
            return new RecommendHistoryPlaceId(
                    place.getOutingGuide().getId().toString(),
                    place.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
            );
        }
    }

    public record RecommendOrigin(
            Float lat,
            Float lng
    ) {
        public static RecommendOrigin from(Float lat, Float lng) {
            return new RecommendOrigin(lat, lng);
        }
    }

    public static RecommendCourseRequest from(
            String query,
            List<OutingCollectionPrivate> places,
            Integer availableTime,
            String category,
            Instant dateTime,
            Float lat,
            Float lng
    ) {

        return new RecommendCourseRequest(
                query,
                places.stream().map(
                        k -> RecommendCandidate.from(k.getOutingGuide())
                ).toList(),
                places.stream().map(RecommendHistoryPlaceId::from).toList(),
                availableTime,
                category,
                dateTime.atZone(ZoneId.systemDefault()).toLocalDateTime(),
                RecommendOrigin.from(lat, lng)
        );
    }
}
