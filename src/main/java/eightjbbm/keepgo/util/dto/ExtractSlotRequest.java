package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;

import java.time.LocalDate;

public record ExtractSlotRequest(
        String chat,
        LocalDate date,
        AiSlot prevSlot,
        String prevQuery
) {
    public static ExtractSlotRequest from(GetReplyRequest request) {
        return new ExtractSlotRequest(
                request.content(),
                request.createdDate(),
                AiSlot.from(request.slot()),
                request.query()
        );
    }
}
