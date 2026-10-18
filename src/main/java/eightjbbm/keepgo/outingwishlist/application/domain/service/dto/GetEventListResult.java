package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/// 여행 위시리스트에 추가한 행사 목록 조회 API 서비스 응답 DTO
///
public record GetEventListResult(
        @Nullable String nextCursor,
        List<Event> events
) {
    public record Event(
            Long eventId,
            String name,
            String category,
            String description,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable Place place
    ) {}

    public record Place(
            String name,
            String addressSg
    ) {}

    public static GetEventListResult from(
            List<OutingCollectionPrivate> wishlistEntries,
            String nextCursor
    ) {
        return new GetEventListResult(
                nextCursor,
                wishlistEntries.stream()
                        .map(wishlistEntry -> {
                            OutingEvent event = (OutingEvent) wishlistEntry.getOutingGuide();
                            return new Event(
                                    wishlistEntry.getId(),
                                    event.getName(),
                                    event.getCategory(),
                                    event.getDescription(),
                                    (event.getStartDatetime() == null)
                                            ? null
                                            : event.getStartDatetime().atZone(ZoneId.systemDefault()).toLocalDate(),
                                    (event.getEndDatetime() == null)
                                            ? null
                                            : event.getEndDatetime().atZone(ZoneId.systemDefault()).toLocalDate(),
                                    new Place(
                                            null,
                                            event.getAddress().toSg()
                                    ));
                        })
                        .toList()
        );
    }
}
