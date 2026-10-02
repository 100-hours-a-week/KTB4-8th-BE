package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.chat.ChatMapper;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.cache.slot.UpdateSlotCacheRequest;
import eightjbbm.keepgo.util.client.geocoding.GeoCodingApiClient;
import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheService;
import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;
import eightjbbm.keepgo.util.cache.query.QueryCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MemberRepository memberRepository;
    private final ChatRepository chatRepository;
    private final GetReplyCacheService getReplyCacheService;
    private final SlotCacheService slotCacheService;
    private final QueryCacheService queryCacheService;
    private final GeoCodingApiClient geoCodingApiClient;

    /// 채팅 전송 API
    /// @param command {@link SendChatCommand}
    /// @return {@link SendChatResult}
    public SendChatResult sendChat(SendChatCommand command) {
        Long memberId = command.memberId();
        Member member = memberRepository.findById(memberId).orElseThrow();
        Chat userChat = chatRepository.save(Chat.from(member, command.content()));
        SlotValue slot = slotCacheService.getSlot(memberId).orElse(
                SlotValue.from(null, null, null, null, null)
        );
        String query = queryCacheService.getQuery(memberId);
        getReplyCacheService.createRequest(
                GetReplyRequest.from(
                        command,
                        userChat.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        slot,
                        query
                )
        );
        return SendChatResult.from(userChat);
    }

    /// 채팅 대답 폴링 API
    public GetReplyResult getReply(GetReplyCommand command) {
        Optional<SlotValue> slotValue = slotCacheService.getSlot(command.memberId());
        return getReplyCacheService.poll(command.memberId())
                .map(response -> {
                    slotCacheService.updateSlot(new UpdateSlotCacheRequest(
                            command.memberId(),
                            Optional.ofNullable(response.slot().origin()).orElse(slotValue.map(SlotValue::getOrigin).orElse(null)),
                            Optional.ofNullable(response.slot().region()).orElse(slotValue.map(SlotValue::getRegion).orElse(null)),
                            Optional.ofNullable(
                                 response.slot().datetime()
                            ).map(
                                    k -> k.atTime(
                                            slotValue
                                                    .map(s -> s.getDatetime().toLocalTime())
                                                    .orElse(null)
                                    )
                            ).orElse(null),
                            Optional.ofNullable(response.slot().availableTime()).orElse(slotValue.map(SlotValue::getAvailableTime).orElse(null)),
                            Optional.ofNullable(response.slot().category()).map(List::of).orElse(slotValue.map(SlotValue::getCategory).orElse(null))
                    ));
                    return GetReplyResult.Completed.from("COMPLETED", response);
                })
                .orElseGet(() -> GetReplyResult.InProgress.from("IN_PROGRESS", 1));
    }

    /// 채팅 내역 조회 API
    /// @param command {@link GetChatsCommand}
    /// @return {@link GetChatsResult}
    public GetChatsResult getChats(GetChatsCommand command) {
        Slice<Chat> chatSlice = chatRepository.findByMemberIdAndIdLessThanOrderByCreatedAtDescIdDesc(
                command.memberId(),
                command.cursor(),
                PageRequest.of(0, command.size() + 1)
        );
        List<Chat> fetched = chatSlice.getContent();
        List<Chat> chats = fetched.subList(0, Math.min(fetched.size(), command.size()));
        boolean hasNext = fetched.size() > command.size();
        Long nextCursor = hasNext ? fetched.getLast().getId() : -1L;
        return GetChatsResult.from(chats, hasNext, nextCursor);
    }

    /// 채팅 내역 초기화 API
    ///
    /// 1차 구현 완료
    /// @param command {@link ResetChatroomCommand}
    @Transactional
    public void resetChatroom(ResetChatroomCommand command) {
        List<Chat> allByMemberId = chatRepository.findAllByMemberId(command.memberId());
        allByMemberId.forEach(Chat::delete);
    }

    /// 의도 카드 수정 API
    @Transactional
    public UpdateSlotResult updateSlot(UpdateSlotCommand command) {
        Optional<SlotValue> slot = slotCacheService.getSlot(command.memberId());
        var request = ChatMapper.INSTANCE.toUpdateSlotCacheRequest(command);
        slotCacheService.updateSlot(request);
        String result = null;
        if (slot.isEmpty() || slot.map(SlotValue::getOrigin).equals(command.userCoordinate())) {
            var response = geoCodingApiClient.mapCoordinatesToLocationName(
                            command.userCoordinate().lat(),
                            command.userCoordinate().lng());

            String level2 = response.response().result().getFirst().structure().level2();
            String level4A = response.response().result().getFirst().structure().level4A();
            result = level2 + " " + level4A;
        }
        return UpdateSlotResult.from(result);
    }
}
