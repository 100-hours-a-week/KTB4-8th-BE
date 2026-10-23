package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;

import java.util.List;

/// 여행 위시리스트에 추가한 장소 목록 조회 API 서비스 응답 DTO
///
public record GetPlaceListResult(
        String nextCursor,
        List<Place> places
) {
    public record Place(
            Long placeId,
            String name,
            String category,
            String description,
            String addressSg
    ) {}

    public static GetPlaceListResult from(
            List<OutingCollectionPrivate> wishlistEntries,
            String nextCursor
    ) {
        return new GetPlaceListResult(
                nextCursor,
                wishlistEntries.stream()
                        .map(outingCollectionPrivate ->
                                new Place(
                                        outingCollectionPrivate.getId(),
                                        outingCollectionPrivate.getOutingGuide().getName(),
                                        outingCollectionPrivate.getOutingGuide().getCategory(),
                                        outingCollectionPrivate.getOutingGuide().getDescription(),
                                        outingCollectionPrivate.getOutingGuide().getAddress().toSg()))
                        .toList()
        );
    }
}
