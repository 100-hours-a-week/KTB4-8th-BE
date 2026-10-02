package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.cache.slot.SlotValue;

import java.time.LocalDate;

public sealed interface GetReplyResponse
        permits GetReplyResponse.Completed, GetReplyResponse.InProgress {

    public record Completed(
            String status,
            Chat chat,
            Slot slot
    ) implements GetReplyResponse {
        public static Completed from(GetReplyResult.Completed completed) {
            return new Completed(
                    completed.status(),
                    Chat.from(completed.chat()),
                    completed.slot()
            );
        }
    }

    /// FE에 내려주는 슬롯
    ///
    /// @param category FE는 카테고리를 하나만 받으므로 첫 번째 값만 내려준다
    public record Slot(
        Coordinate origin,
        String region,
        LocalDate datetime,
        Integer availableTime,
        String category
    ) {
        public static Slot from(SlotValue slot) {
            if (slot == null) {
                return new Slot(null, null, null, null, null);
            }
            return new Slot(
                    slot.getOrigin(),
                    slot.getRegion(),
                    slot.getDatetime() == null ? null : slot.getDatetime().toLocalDate(),
                    slot.getAvailableTime(),
                    slot.getCategory() == null || slot.getCategory().isEmpty() ? null : slot.getCategory().getFirst()
            );
        }
    }

    public record InProgress(
            String status,
            Integer pollAfter
    ) implements GetReplyResponse {
        public static InProgress from(GetReplyResult.InProgress inProgress) {
            return new InProgress(
                    inProgress.status(),
                    inProgress.pollAfter()
            );
        }
    }

    record Chat(
            String content,
            Boolean isByBot
    ) {
        public static Chat from(GetReplyResult.Chat chat) {
            return new Chat(
                    chat.content(),
                    chat.isByBot()
            );
        }
    }
}
