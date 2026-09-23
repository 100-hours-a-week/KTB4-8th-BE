package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;
import java.util.List;

public record ExtractSlotRequest(
        String chat,
        LocalDate date,
        PrevSlot prevSlot,
        String prevQuery
) {
    public record PrevSlot(
            OriginalCoordinate origin,
            String region,
            LocalDate datetime,
            Integer availableTime,
            List<String> categories
    ) {
        public record OriginalCoordinate(
                Float lat,
                Float lng
        ) {}
    }

    public static ExtractSlotRequest from(
            String chat,
            LocalDate createdDate,
            Float lat,
            Float lng,
            String region,
            LocalDate datetime,
            Integer availableTime,
            List<String> category,
            String query
    ) {
        return new ExtractSlotRequest(
                chat,
                createdDate,
                new PrevSlot(
                        new PrevSlot.OriginalCoordinate(lat, lng),
                        region,
                        datetime,
                        availableTime,
                        category
                ),
                query
        );
    }
}
