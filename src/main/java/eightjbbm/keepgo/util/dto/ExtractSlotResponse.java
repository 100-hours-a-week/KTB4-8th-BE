package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.chat.dto.GetReplyResponse;
import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDate;

public sealed interface ExtractSlotResponse {
    public record Success(
            String message,
            ExtractData data
    ) implements ExtractSlotResponse {}

    public record ExtractData(
            ExtractSlot slot,
            String query,
            String botMessage
    ) {}

    public record ExtractSlot(
            Coordinate origin,
            String region,
            LocalDate datetime,
            Integer availableTime,
            String category
    ) {}

    public record Error(
            String message,
            Object data
    ) implements ExtractSlotResponse {}
}
