package eightjbbm.keepgo.chat.dto;

public sealed interface GetReplyResponse
        permits GetReplyResponse.Completed, GetReplyResponse.InProgress {

    public record Completed(
            String status,
            Chat chat
    ) implements GetReplyResponse {
        public static Completed from(GetReplyResult.Completed completed) {
            return new Completed(
                    completed.status(),
                    Chat.from(completed.chat())
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
