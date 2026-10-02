package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.chat.dto.GetReplyResponse;

import java.time.LocalDate;

public record ExtractSlotResponse(
        String message,
        ExtractData data
) {
    public record ExtractData(
            GetReplyResponse.Slot slot,
            String query,
            String botMessage
    ) {
        public record ExtractSlot(
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
}
