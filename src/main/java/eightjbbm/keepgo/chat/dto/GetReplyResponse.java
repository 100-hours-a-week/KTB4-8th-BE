package eightjbbm.keepgo.chat.dto;

public sealed interface GetReplyResponse
        permits GetReplyResponse.Completed, GetReplyResponse.inProgress {
    record Completed(
            String status,
            Chat chat
    ) implements GetReplyResponse {}

    record inProgress(
            String status,
            Integer pollAfter
    ) implements GetReplyResponse {}

    record Chat(
            String content,
            Boolean isByBot
    ) {}
}
