package eightjbbm.keepgo.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 *
 * @param content 사용자가 작성한 채팅의 본문
 */
public record SendChatRequest(
        @NotBlank String content
) {
}
