package eightjbbm.keepgo.util.cache.getreply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.chat.service.AiServerExtractSlotApiService;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("응답 캐시 워커의 나들이 조건 추출")
class GetReplyWorkerTest {
    private static final Long MEMBER_ID = 1L;

    private final AiServerExtractSlotApiService ai = mock(AiServerExtractSlotApiService.class);
    private final GetReplyCacheRepository cache = mock(GetReplyCacheRepository.class);
    private final OutingCriteriaRepository criteriaRepository = mock(OutingCriteriaRepository.class);
    private final GetReplyWorker worker = new GetReplyWorker(ai, cache, criteriaRepository);
    private final OutingCriteria previous = OutingCriteria.emptyOutingCriteriaWithMemberId(MEMBER_ID);
    private final ExtractSlotRequest request = new ExtractSlotRequest("카페 가고 싶어", LocalDate.of(2026, 10, 20), previous);

    @Test
    @DisplayName("done의 나들이 조건과 봇 메시지를 저장한다")
    void savesDone() {
        var datetime = LocalDateTime.of(2026, 10, 21, 14, 0);
        var slot = new ExtractSlotResponseV2.Slot(null, "강남", datetime, "180", Set.of("카페"));
        when(ai.extractSlot(any(), any(), any())).thenReturn(new ExtractSlotResponseV2(slot, "조용한", "찾아볼게요."));
        when(criteriaRepository.findById(MEMBER_ID)).thenReturn(Optional.of(previous));

        worker.requestGetReply(MEMBER_ID, request).join();

        verify(cache).update(MEMBER_ID, "찾아볼게요.");
        var saved = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(criteriaRepository).save(saved.capture());
        assertThat(saved.getValue().getUserAvailableTimeMinutes()).isEqualTo(180);
        assertThat(saved.getValue().getRequestedDatetime())
                .isEqualTo(datetime.atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }

    @Test
    @DisplayName("done에 봇 메시지가 없으면 실패로 표시하고 저장하지 않는다")
    void failsWhenBotMessageMissing() {
        var slot = new ExtractSlotResponseV2.Slot(null, null, null, null, null);
        when(ai.extractSlot(any(), any(), any())).thenReturn(new ExtractSlotResponseV2(slot, null, null));

        worker.requestGetReply(MEMBER_ID, request).join();

        verify(cache).setStatusAsFail(MEMBER_ID);
        verify(criteriaRepository, never()).save(any());
    }
}
