package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestGuard;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.client.geocoding.GeoCodingApiClient;
import eightjbbm.keepgo.util.exception.BadRequestException;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRequestGuard requestGuard;
    private final ChatRepository chatRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;
    private final GeoCodingApiClient geoCodingApiClient;

    /// 채팅 내역 조회 API
    /// @param command {@link GetChatsCommand}
    /// @return {@link GetChatsResult}
    @Transactional(readOnly = true)
    public GetChatsResult getChats(GetChatsCommand command) {
        int size = command.size() == null ? 20 : command.size();

        if (size < 1 || size > 100 || (command.cursor() != null && command.cursor() <= 0)) {
            throw new BadRequestException();
        }

        List<Chat> fetched =
                chatRepository.findVisibleChats(command.memberId(), command.cursor(), PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;

        List<Chat> chats = List.copyOf(fetched.subList(0, Math.min(fetched.size(), size)));

        // 실제 반환한 마지막 항목을 커서로 사용한다.
        Long nextCursor = hasNext ? chats.getLast().getId() : -1L;

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
        try (var lease = requestGuard.acquire(command.memberId())) {
            // 기존 updateSlot() 본문 전체를 그대로 둡니다.
            OutingCriteria outingCriteria = outingCriteriaRepository
                    .findById(command.memberId())
                    .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(command.memberId()));
            outingCriteriaRepository.save(outingCriteria.update(
                    command.userCoordinate(),
                    command.requestedLocationName(),
                    command.requestedDateTime().atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                    command.availableTime(),
                    command.categories(),
                    null));
            var response = geoCodingApiClient.mapCoordinatesToLocationName(
                    command.userCoordinate().lat(), command.userCoordinate().lng());

            String level2 = response.response().result().getFirst().structure().level2();
            String level4A = response.response().result().getFirst().structure().level4A();
            String result = level2 + " " + level4A;
            return UpdateSlotResult.from(result);
        }
    }
}
