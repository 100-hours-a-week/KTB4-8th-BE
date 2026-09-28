package eightjbbm.keepgo.chat.dto;

/// @param memberId 회원 ID
/// @param content 채팅의 내용
public record SendChatCommand(
        Long memberId,
        String content
) {
}
