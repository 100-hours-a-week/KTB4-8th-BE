package eightjbbm.keepgo.chat.dto;

public record SendChatCommand(
        Long userId,
        String content
) {
}
