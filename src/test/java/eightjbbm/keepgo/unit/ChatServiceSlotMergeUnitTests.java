package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.chat.dto.GetReplyCommand;
import eightjbbm.keepgo.chat.dto.GetReplyResponse;
import eightjbbm.keepgo.chat.dto.GetReplyResult;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.service.ChatService;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheService;
import eightjbbm.keepgo.util.cache.query.QueryCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotCacheRepository;
import eightjbbm.keepgo.util.cache.slot.SlotCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.cache.slot.UpdateSlotCacheRequest;
import eightjbbm.keepgo.util.client.geocoding.GeoCodingApiClient;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// 챗봇 응답 조회 시 슬롯 병합 단위 테스트
///
/// 기존 슬롯이 있고 그 날짜가 비어 있을 때 응답 조회가 NPE로 500을 내던 문제(2026-10-02)의 회귀 테스트
public class ChatServiceSlotMergeUnitTests {

    private static final Long MEMBER_ID = 1L;

    private GetReplyCacheService getReplyCacheService;
    private SlotCacheService slotCacheService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        getReplyCacheService = mock(GetReplyCacheService.class);
        slotCacheService = new SlotCacheService(new SlotCacheRepository(new ConcurrentMapCacheManager("slot")));
        chatService = new ChatService(
                mock(MemberRepository.class),
                mock(ChatRepository.class),
                getReplyCacheService,
                slotCacheService,
                mock(QueryCacheService.class),
                mock(GeoCodingApiClient.class)
        );
    }

    @Test
    @DisplayName("""
            기존 슬롯의 날짜가 비어 있어도 응답 조회가 실패하지 않고 새 값만 덮어쓴다
            """)
    void mergeWhenPreviousDatetimeIsNull() {
        slotCacheService.updateSlot(new UpdateSlotCacheRequest(MEMBER_ID, null, null, null, 180, null));
        when(getReplyCacheService.poll(MEMBER_ID)).thenReturn(Optional.of(new ExtractSlotResponse.ExtractData(
                new GetReplyResponse.Slot(null, "서울 강남구", null, null, null), "", "강남으로 찾아볼게요."
        )));

        GetReplyResult result = chatService.getReply(new GetReplyCommand(MEMBER_ID, 1L));

        assertThat(result).isInstanceOf(GetReplyResult.Completed.class);
        SlotValue slot = slotCacheService.getSlot(MEMBER_ID).orElseThrow();
        assertThat(slot.getRegion()).isEqualTo("서울 강남구");
        assertThat(slot.getDatetime()).isNull();
        assertThat(slot.getAvailableTime()).isEqualTo(180);
    }

    @Test
    @DisplayName("""
            AI가 비운 필드는 기존 슬롯 값을 유지한다
            """)
    void keepPreviousValuesForMissingFields() {
        LocalDateTime saturday = LocalDateTime.of(2026, 10, 3, 15, 0);
        slotCacheService.updateSlot(new UpdateSlotCacheRequest(MEMBER_ID, null, "서울 성동구", saturday, 360, List.of("카페")));
        when(getReplyCacheService.poll(MEMBER_ID)).thenReturn(Optional.of(new ExtractSlotResponse.ExtractData(
                new GetReplyResponse.Slot(null, null, null, null, null), "", "알겠어요."
        )));

        chatService.getReply(new GetReplyCommand(MEMBER_ID, 1L));

        SlotValue slot = slotCacheService.getSlot(MEMBER_ID).orElseThrow();
        assertThat(slot.getRegion()).isEqualTo("서울 성동구");
        assertThat(slot.getDatetime()).isEqualTo(saturday);
        assertThat(slot.getAvailableTime()).isEqualTo(360);
        assertThat(slot.getCategory()).containsExactly("카페");
    }

    @Test
    @DisplayName("""
            처리 중이면 IN_PROGRESS를 돌려준다
            """)
    void inProgress() {
        when(getReplyCacheService.poll(MEMBER_ID)).thenReturn(Optional.empty());

        assertThat(chatService.getReply(new GetReplyCommand(MEMBER_ID, 1L)))
                .isInstanceOf(GetReplyResult.InProgress.class);
    }
}
