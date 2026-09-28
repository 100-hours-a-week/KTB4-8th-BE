package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.chat.dto.SendChatCommand;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GetReplyRequest(
        Long memberId,
        String content,
        LocalDate createdDate,
        SlotValue slot,
        String query
) {
    public static GetReplyRequest from(
          SendChatCommand command,
          LocalDate date,
          SlotValue slot,
          String query
    ) {
        return new GetReplyRequest(
                command.memberId(),
                command.content(),
                date,
                slot,
                query
        );
    }
}
