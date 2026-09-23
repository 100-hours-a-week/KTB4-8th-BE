package eightjbbm.keepgo.chat.dto;

public record GetReplyCommand(
        Long memberId,
        Long chatId
) {
}
