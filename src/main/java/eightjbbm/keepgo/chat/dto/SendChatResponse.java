package eightjbbm.keepgo.chat.dto;

public record SendChatResponse(
        String content,
        Boolean isByBot
) {
    public static SendChatResponse from(SendChatResult result) {
        return new SendChatResponse(
                result.content(),
                result.isByBot()
        );
    }
}
