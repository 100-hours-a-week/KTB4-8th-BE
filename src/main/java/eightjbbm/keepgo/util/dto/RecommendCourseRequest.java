package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/// AI 코스 추천 요청 (AI app/schemas/recommend_courses.py 의 RecommendCoursesRequest)
///
/// @param query {@code String} 사용자 채팅의 정성 조건. AI 필수 값이라 없으면 빈 문자열
/// @param candidates {@code List<RecommendCandidate>} 장소 후보 (최대 50개)
/// @param historyPlaceIds {@code List<RecommendHistoryPlaceId>} 장소 후보가 등록된 일자 (최대 50개)
/// @param availableTime {@code String} 외출 가능 시간(분) "180"·"360"·"540"
/// @param category {@code List<String>} AI가 허용하는 카테고리 목록
/// @param datetime {@code String} 희망 일시 "yyyy-MM-dd" 또는 "yyyy-MM-dd HH:mm"
/// @param origin {@code Coordinate} 사용자의 출발 위치
public record RecommendCourseRequest(
        String query,
        List<RecommendCandidate> candidates,
        List<RecommendHistoryPlaceId> historyPlaceIds,
        String availableTime,
        List<String> category,
        String datetime,
        Coordinate origin
) {
    /// AI가 받는 후보·이력 최대 개수
    public static final int MAX_PLACES = 50;

    /// 장소 좌표는 아직 수집하지 않아 비워 보낸다. AI는 좌표가 없으면 이동시간 없이 코스를 만든다.
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
                    place.getDescription() == null ? "" : place.getDescription(),
                    null,
                    null,
                    place.getBusinessHours() == null ? "정보 없음" : place.getBusinessHours()
            );
        }
    }

    public record RecommendHistoryPlaceId(
            String placeId,
            LocalDate savedAt
    ) {
        /// 저장 시각을 기록하기 전에 저장된 장소는 createdAt이 비어 있어 오늘 날짜로 보낸다.
        public static RecommendHistoryPlaceId from(OutingCollectionPrivate place) {
            return new RecommendHistoryPlaceId(
                    place.getOutingGuide().getId().toString(),
                    place.getCreatedAt() == null
                            ? LocalDate.now()
                            : place.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
            );
        }
    }

    /// @param places 회원의 저장 장소. 장소(guide)가 겹치지 않고 최대 {@link #MAX_PLACES}개여야 한다
    public static RecommendCourseRequest from(
            String query,
            List<OutingCollectionPrivate> places,
            SlotValue slot
    ) {
        return new RecommendCourseRequest(
                query == null ? "" : query,
                places.stream().map(k -> RecommendCandidate.from(k.getOutingGuide())).toList(),
                places.stream().map(RecommendHistoryPlaceId::from).toList(),
                AiSlot.formatAvailableTime(slot.getAvailableTime()),
                AiSlot.filterCategories(slot.getCategory()),
                AiSlot.formatDatetime(slot.getDatetime()),
                validOrigin(slot.getOrigin())
        );
    }

    /// AI는 좌표의 lat·lng를 모두 요구하므로 하나라도 비면 출발지 없이 보낸다.
    private static Coordinate validOrigin(Coordinate origin) {
        return origin != null && origin.lat() != null && origin.lng() != null ? origin : null;
    }
}
