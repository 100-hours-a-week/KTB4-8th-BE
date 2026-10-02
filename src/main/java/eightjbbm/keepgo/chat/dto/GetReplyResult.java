package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.dto.ExtractSlotResponse;

public sealed interface GetReplyResult
        permits GetReplyResult.Completed, GetReplyResult.InProgress {

    record Completed(
            String status,
            Chat chat,
            GetReplyResponse.Slot slot
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, ExtractSlotResponse.ExtractData response) {
            return new Completed(
                    status, new Chat(response.botMessage(), true), response.slot()
            );
        }
    }

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
