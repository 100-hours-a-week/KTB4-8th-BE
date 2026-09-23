package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;

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
            String category
    ) {
        public record OriginalCoordinate(
                Float lat,
                Float lng
        ) {}
    }


    public static ExtractSlotRequest from(
            String chat,
            LocalDate datetime,
            SlotValue slotValue
    ) {
        return new ExtractSlotRequest(
                chat,
                datetime,
                new PrevSlot(
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                slotValue.getQuery()
        );
    }
}
