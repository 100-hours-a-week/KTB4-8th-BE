package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.Chat;
import eightjbbm.keepgo.chat.ChatRepository;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MemberRepository memberRepository;
    private final ChatRepository chatRepository;
    private final AiServerClient aiServerClient;

    public SendChatResult sendChat(SendChatCommand command) {
        /*
        채팅 전송 API
        요청 본문: 채팅 본문
        1. 채팅 저장
        2. AI 서버에게 채팅 전달(아마 POST /extract로 기억)
        */
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        chatRepository.save(new Chat(member, command.content(), false));
        //aiServerClient.extractSlot(...);
        return null;
    }

    public GetChatsResult getChats(GetChatsCommand command) {
        /*
        채팅 내역 조회 API
        1. authentication에서 회원 정보 추출
        2. 채팅 테이블에서 조회 후 반환
        */
        List<Chat> allByMemberId = chatRepository.findAllByMemberId(command.userId());
        return null;
    }

    public ResetChatroomResult resetChatroom(ResetChatroomCommand command) {
        /*
        채팅 내역 초기화 API
        1. authentication에서 회원 정보 추출
        2. Chat.delete()?
        */
        List<Chat> allbyMemberId = chatRepository.findAllByMemberId(command.userId());

        return null;
    }
}
