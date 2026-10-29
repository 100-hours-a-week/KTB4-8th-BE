package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.util.Coordinate;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/// 여행 위시리스트에 추가한 행사 상세 조회 API 서비스 응답 DTO
///
public record GetEventDetailResult(
        Long eventId,
        String name,
        String category,
        String description,
        List<String> videosFeaturedLink,
        LocalDate saveDate,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable Place place) {
    public record Place(String name, String addressSg, String addressRoad, Coordinate coordinate) {}

    public static GetEventDetailResult from(OutingWishlistEntry wishlistEntry) {
        OutingEvent event = (OutingEvent) wishlistEntry.getOutingGuide();
        return new GetEventDetailResult(
                wishlistEntry.getId(),
                event.getName(),
                event.getCategory(),
                event.getDescription(),
                null,
                wishlistEntry.getCreateDatetime().atZone(ZoneId.systemDefault()).toLocalDate(),
                event.getStartDatetime() == null
                        ? null
                        : event.getStartDatetime()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate(),
                event.getEndDatetime() == null
                        ? null
                        : event.getEndDatetime().atZone(ZoneId.systemDefault()).toLocalDate(),
                new Place(
                        null,
                        (event.getAddress() == null) ? null : event.getAddress().toSg(),
                        (event.getAddress() == null) ? null : event.getAddress().toRoad(),
                        event.getCoordinate()));
    }
}
