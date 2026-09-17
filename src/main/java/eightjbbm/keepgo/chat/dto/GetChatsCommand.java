package eightjbbm.keepgo.chat.dto;

public record GetChatsCommand(
        Long userId,
        Long cursor,
        Integer size
) {
}
