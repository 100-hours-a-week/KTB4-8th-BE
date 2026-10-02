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
import eightjbbm.keepgo.util.dto.AiSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
        return getReplyCacheService.poll(command.memberId())
                .map(response -> GetReplyResult.Completed.from(
                        "COMPLETED", response, mergeSlot(command.memberId(), response.slot())
                ))
                .orElseGet(() -> GetReplyResult.InProgress.from("IN_PROGRESS", 1));
    }

    /// AI가 돌려준 슬롯 중 값이 있는 필드만 기존 슬롯에 덮어쓰고, 병합 결과를 돌려준다.
    ///
    /// poll()이 응답을 캐시에서 이미 꺼낸 뒤라, 여기서 예외가 나면 챗봇 답변이 사라진다.
    /// 그래서 기존 슬롯이나 그 필드가 비어 있어도 예외 없이 병합해야 한다.
    /// 출발지는 BE가 좌표로 관리하므로 AI가 준 출발지 이름으로 덮어쓰지 않는다.
    private SlotValue mergeSlot(Long memberId, AiSlot next) {
        SlotValue prev = slotCacheService.getSlot(memberId)
                .orElse(SlotValue.from(null, null, null, null, null));
        if (next == null) {
            return prev;
        }
        var request = new UpdateSlotCacheRequest(
                memberId,
                prev.getOrigin(),
                firstNonNull(next.region(), prev.getRegion()),
                mergeDatetime(AiSlot.parseDatetime(next.datetime()), prev.getDatetime()),
                firstNonNull(AiSlot.parseAvailableTime(next.availableTime()), prev.getAvailableTime()),
                firstNonNull(next.category(), prev.getCategory())
        );
        slotCacheService.updateSlot(request);
        return slotCacheService.getSlot(memberId).orElseThrow();
    }

    /// AI가 날짜만 줬고(00:00) 기존 슬롯이 같은 날짜에 시각까지 갖고 있으면 기존 시각을 유지한다.
    private static LocalDateTime mergeDatetime(LocalDateTime next, LocalDateTime prev) {
        if (next == null) {
            return prev;
        }
        boolean dateOnly = next.toLocalTime().equals(LocalTime.MIDNIGHT);
        if (dateOnly && prev != null && prev.toLocalDate().equals(next.toLocalDate())) {
            return prev;
        }
        return next;
    }

    private static <T> T firstNonNull(T next, T prev) {
        return next != null ? next : prev;
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
    /// 대화에서 쌓은 슬롯과 아직 꺼내지 않은 응답도 함께 지운다.
    /// 남겨 두면 새 대화가 이전 조건을 그대로 물려받는다.
    /// @param command {@link ResetChatroomCommand}
    @Transactional
    public void resetChatroom(ResetChatroomCommand command) {
        List<Chat> allByMemberId = chatRepository.findAllByMemberId(command.memberId());
        allByMemberId.forEach(Chat::delete);
        slotCacheService.deleteSlot(command.memberId());
        getReplyCacheService.discard(command.memberId());
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
