package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;
import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ExtractSlotRequest(
        String chat,
        LocalDate date,
        Slot prevSlot,
        String prevQuery
) {
    public record Slot(
            Coordinate origin,
            String region,
            LocalDate datetime,
            Integer availableTime,
            List<String> category
    ) {

    }

    public static ExtractSlotRequest from(GetReplyRequest request) {
        return new ExtractSlotRequest(
                request.content(),
                request.createdDate(),
                new Slot(
                        request.slot().getOrigin(),
                        request.slot().getRegion(),
                        request.slot().getDatetime().toLocalDate(),
                        request.slot().getAvailableTime(),
                        request.slot().getCategory()
                ),
                request.query()
        );
    }
}
