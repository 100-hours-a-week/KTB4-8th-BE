package eightjbbm.keepgo.chat.dto;

public sealed interface GetReplyResult
        permits GetReplyResult.Completed, GetReplyResult.InProgress {

    record Completed(
            String status,
            Chat chat
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, String content) {
            return new Completed(
                    status, new Chat(content, true)
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
