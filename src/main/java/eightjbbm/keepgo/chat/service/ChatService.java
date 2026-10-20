package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheService;
import eightjbbm.keepgo.util.cache.getreply.GetReplyJobStatus;
import eightjbbm.keepgo.util.client.geocoding.GeoCodingApiClient;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MemberRepository memberRepository;
    private final ChatRepository chatRepository;
    private final GetReplyCacheService getReplyCacheService;
    private final OutingCriteriaRepository outingCriteriaRepository;
    private final GeoCodingApiClient geoCodingApiClient;

    /// 채팅 전송 API
    /// @param command {@link SendChatCommand}
    /// @return {@link SendChatResult}
    public SendChatResult sendChat(SendChatCommand command) {
        Long memberId = command.memberId();
        Member member = memberRepository.findById(memberId).orElseThrow();
        Chat userChat = chatRepository.save(Chat.from(member, command.content()));
        OutingCriteria outingCriteria = outingCriteriaRepository
                .findById(memberId)
                .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(command.memberId()));
        getReplyCacheService.createRequest(
                command.memberId(),
                command.content(),
                userChat.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                outingCriteria);
        return SendChatResult.from(userChat);
    }

    /// 채팅 대답 폴링 API
    public GetReplyResult getReply(GetReplyCommand command) {
        // getReply 상태 확인
        GetReplyJobStatus status = getReplyCacheService.checkStatus(command.memberId());
        switch (status) {
            case PENDING -> { return GetReplyResult.InProgress.from("IN_PROGRESS", 1); }
            case FAILED -> throw new ServiceUnavailableException();
            case SUCCESS -> {
                String reply = getReplyCacheService.getReply(command.memberId());
                return GetReplyResult.Completed.from(
                        "COMPLETED",
                        reply,
                        outingCriteriaRepository.findById(command.memberId()).get()
                );
            }
        }
        return null;
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
    ///
    @Transactional
    public UpdateSlotResult updateSlot(UpdateSlotCommand command) {
        OutingCriteria outingCriteria = outingCriteriaRepository.findById(command.memberId())
                .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(command.memberId()));
        outingCriteriaRepository.save(
                outingCriteria.update(
                        command.userCoordinate(),
                        command.requestedLocationName(),
                        command.requestedDateTime().toInstant(ZoneOffset.UTC),
                        command.availableTime(),
                        command.categories(),
                        null));
        var response = geoCodingApiClient.mapCoordinatesToLocationName(
                command.userCoordinate().lat(),
                command.userCoordinate().lng());

        String level2 = response.response().result().getFirst().structure().level2();
        String level4A = response.response().result().getFirst().structure().level4A();
        String result = level2 + " " + level4A;
        return UpdateSlotResult.from(result);
    }
}
