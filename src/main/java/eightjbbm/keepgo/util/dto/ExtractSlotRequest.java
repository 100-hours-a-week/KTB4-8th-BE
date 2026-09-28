package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;
import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;

public record ExtractSlotRequest(
        String chat,
        LocalDate date,
        SlotValue prevSlot,
        String prevQuery
) {
    public static ExtractSlotRequest from(GetReplyRequest request) {
        return new ExtractSlotRequest(
                request.content(),
                request.createdDate(),
                request.slot(),
                request.query()
        );
    }
}
