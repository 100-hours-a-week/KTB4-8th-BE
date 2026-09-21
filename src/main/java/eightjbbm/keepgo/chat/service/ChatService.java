package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.Chat;
import eightjbbm.keepgo.chat.ChatRepository;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MemberRepository memberRepository;
    private final ChatRepository chatRepository;
    private final AiServerClient aiServerClient;

    /// 채팅 전송 API
    /// @param command {@link SendChatCommand}
    /// @return {@link SendChatResult}
    public SendChatResult sendChat(SendChatCommand command) {
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        Chat userChat = chatRepository.save(new Chat(member, command.content(), false));
        aiServerClient.extractSlot();
        return SendChatResult.from(userChat);
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
        List<Chat> allbyMemberId = chatRepository.findAllByMemberId(command.userId());
        for (Chat chat: allbyMemberId) {
            chat.delete();
        }
    }
}
