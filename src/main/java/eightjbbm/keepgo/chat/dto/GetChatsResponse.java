package eightjbbm.keepgo.chat.dto;

import java.util.List;

/// @param hasNext
/// @param nextCursor
/// @param chats {@link Chat}
public record GetChatsResponse(
        Boolean hasNext,
        Long nextCursor,
        List<Chat> chats
) {
    /// @param content
    /// @param isByBot
    public record Chat(
            String content,
            Boolean isByBot
    ) {}

    public static GetChatsResponse from(GetChatsResult result) {
        return new GetChatsResponse(
                result.hasNext(),
                result.nextCursor(),
                result.chats().stream().map(chat -> new Chat(chat.content(), chat.isByBot())).toList()
        );
    }
}
