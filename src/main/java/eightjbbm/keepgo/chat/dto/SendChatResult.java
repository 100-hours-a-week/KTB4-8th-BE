package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.chat.entity.Chat;

/// @param chatId 채팅 ID
/// @param content 채팅의 내용
/// @param isByBot 챗봇이 작성한 채팅인지 여부
public record SendChatResult(
        Long chatId,
        String content,
        Boolean isByBot
) {
    public static SendChatResult from(Chat chat) {
        return new SendChatResult(
                chat.getId(),
                chat.getContent(),
                false
        );
    }
}
