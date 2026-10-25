package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/// 여행 위시리스트에 추가한 장소 상세 조회 API 서비스 응답 DTO
///
public record GetPlaceDetailResult(
        Long placeId,
        String name,
        String category,
        String description,
        List<String> videosFeaturedLink,
        LocalDate saveDate,
        String addressSg,
        String addressRoad,
        Coordinate coordinate
) {
    public static GetPlaceDetailResult from(OutingWishlistEntry wishlistEntry) {
        OutingPlace place = (OutingPlace) wishlistEntry.getOutingGuide();
        return new GetPlaceDetailResult(
                wishlistEntry.getId(),
                place.getName(),
                place.getCategory(),
                place.getDescription(),
                null,
                wishlistEntry.getCreateDatetime().atZone(ZoneId.systemDefault()).toLocalDate(),
                place.getAddress().toSg(),
                place.getAddress().toRoad(),
                place.getCoordinate()
        );
    }
}
