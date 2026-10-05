package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

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
                    new Slot(
                            completed.slot().origin(),
                            completed.slot().region(),
                            completed.slot().datetime(),
                            completed.slot().availableTime(),
                            completed.slot().category()
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
