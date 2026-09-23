package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.Chat;
import eightjbbm.keepgo.chat.ChatRepository;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheService;
import eightjbbm.keepgo.util.cache.getreply.GetReplyRequest;
import eightjbbm.keepgo.util.cache.slot.SlotCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MemberRepository memberRepository;
    private final ChatRepository chatRepository;
    private final GetReplyCacheService getReplyCacheService;
    private final SlotCacheService slotCacheService;

    /// 채팅 전송 API
    /// @param command {@link SendChatCommand}
    /// @return {@link SendChatResult}
    public SendChatResult sendChat(SendChatCommand command) {
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        Chat userChat = chatRepository.save(new Chat(member, command.content(), false));
        SlotValue slot = slotCacheService.getSlot(command.userId());
        getReplyCacheService.createRequest(
                GetReplyRequest.from(
                        command.userId(),
                        command.content(),
                        userChat.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        slot.getLat(),
                        slot.getLng(),
                        slot.getRequestedLocationName(),
                        slot.getRequestedDate(),
                        slot.getAvailableTime(),
                        slot.getCategories(),
                        slot.getQuery()
                )
        );
        return SendChatResult.from(userChat);
    }

    /// 채팅 대답 폴링 API
    public GetReplyResult getReply(GetReplyCommand command) {
        return getReplyCacheService.poll(command.memberId())
                .map(content -> GetReplyResult.Completed.from("COMPLETED", content))
                .orElseGet(() -> GetReplyResult.InProgress.from("IN_PROGRESS", 1));
    }

    /// 채팅 내역 조회 API
    /// @param command {@link GetChatsCommand}
    /// @return {@link GetChatsResult}
    public GetChatsResult getChats(GetChatsCommand command) {
        Slice<Chat> chatSlice = chatRepository.findByMemberIdAndIdLessThanOrderByCreatedAtDescIdDesc(command.userId(), command.cursor(), PageRequest.of(0, command.size() + 1));
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
    public void resetChatroom(ResetChatroomCommand command) {
        List<Chat> allByMemberId = chatRepository.findAllByMemberId(command.userId());
        allByMemberId.forEach(Chat::delete);
    }

    public void updateSlot() {

    }
}
