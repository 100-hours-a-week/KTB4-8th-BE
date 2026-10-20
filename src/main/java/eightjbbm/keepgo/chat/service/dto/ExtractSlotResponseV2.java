package eightjbbm.keepgo.chat.service.dto;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDateTime;
import java.util.Set;

public interface ExtractSlotResponseV2 {
    record Success(
            String message,
            SuccessData data
    ) implements ExtractSlotResponseV2 {}

    record SuccessData(
            Slot slot,
            String query,
            String botMessage
    ) {}

    record Slot(
            Coordinate origin,
            String region,
            LocalDateTime datetime,
            String availableTime,
            Set<String> category
    ) {}

    record Failure(
            String message,
            Object data
    ) implements ExtractSlotResponseV2 {}
}
