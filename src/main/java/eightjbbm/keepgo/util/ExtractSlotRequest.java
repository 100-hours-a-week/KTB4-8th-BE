package eightjbbm.keepgo.util;

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
}
