package eightjbbm.keepgo.chat.dto;

/// @param userId 회원 ID
/// @param content 채팅의 내용
public record SendChatCommand(
        Long userId,
        String content
) {
}
