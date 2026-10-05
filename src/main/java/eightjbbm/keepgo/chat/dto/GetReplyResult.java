package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;

import java.time.LocalDateTime;
import java.util.Set;

public sealed interface GetReplyResult
        permits GetReplyResult.Completed, GetReplyResult.InProgress {

    record Completed(
            String status,
            Chat chat,
            Slot slot
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, String content, SlotValue slotValue) {
            return new Completed(
                    status, new Chat(content, true),
                    new Slot(
                            slotValue.getOrigin(),
                            slotValue.getRegion(),
                            slotValue.getDatetime(),
                            slotValue.getAvailableTime(),
                            slotValue.getCategory()
                    )
            );
        }
    }

    public record Slot(
            Coordinate origin,
            String region,
            LocalDateTime datetime,
            Integer availableTime,
            Set<String> category
    ) {}

    record InProgress(
            String status,
            Integer pollAfter
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, Integer pollAfter){
            return new InProgress(status, pollAfter);
        }
    }

    record Chat(
            String content,
            Boolean isByBot
    ) {}
}
