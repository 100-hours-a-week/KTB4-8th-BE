package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.chat.entity.Chat;

import java.util.List;

/// @param hasNext 다음 페이지가 존재하는 지
/// @param nextCursor 다음 페이지를 가리키는 토큰 (hasNext == false라면 -1)
/// @param chats 채팅 목록
public record GetChatsResult(
        Boolean hasNext,
        Long nextCursor,
        List<ChatHistory> chats
) {
    record ChatHistory(
            String content,
            Boolean isByBot
    ) {}

    public static GetChatsResult from(List<Chat> chats, Boolean hasNext, Long nextCursor) {
        return new GetChatsResult(
                hasNext,
                nextCursor,
                chats.stream().map(chat -> new ChatHistory(chat.getContent(), chat.getIsByBot())).toList()
        );
    }
}
