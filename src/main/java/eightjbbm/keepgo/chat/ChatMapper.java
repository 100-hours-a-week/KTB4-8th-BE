package eightjbbm.keepgo.chat;

import eightjbbm.keepgo.chat.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMapper {

    @Mapping(source = "request.location", target = "userCoordinate")
    UpdateSlotCommand toUpdateSlotCommand(Long memberId, UpdateSlotRequest request);

    GetChatsCommand toGetChatsCommand(Long memberId, Long cursor, Integer size);

    ResetChatroomCommand toResetChatroomCommand(Long memberId);
}
