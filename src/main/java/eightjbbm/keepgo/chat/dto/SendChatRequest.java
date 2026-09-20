package eightjbbm.keepgo.chat.dto;

/**
 *
 * @param content 사용자가 작성한 채팅의 본문
 */
public record SendChatRequest(
        String content
) {
}
