package eightjbbm.keepgo.chat;

import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.util.cache.slot.UpdateSlotCacheRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ChatMapper {

    ChatMapper INSTANCE = Mappers.getMapper(ChatMapper.class);

    SendChatCommand toSendChatCommand(Long memberId, SendChatRequest request);

    SendChatResponse toResponse(SendChatResult result);

    @Mapping(source = "request.location", target = "userCoordinate")
    UpdateSlotCommand toUpdateSlotCommand(Long memberId, UpdateSlotRequest request);

    GetReplyCommand toGetReplyCommand(Long memberId, Long chatId);

    GetChatsCommand toGetChatsCommand(Long memberId, Long cursor, Integer size);

    ResetChatroomCommand toResetChatroomCommand(Long memberId);

    @Mapping(source = "userCoordinate", target = "coordinate")
    UpdateSlotCacheRequest toUpdateSlotCacheRequest(UpdateSlotCommand command);
}
