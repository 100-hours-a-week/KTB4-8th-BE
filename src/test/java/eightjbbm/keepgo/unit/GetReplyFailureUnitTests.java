package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheRepository;
import eightjbbm.keepgo.util.cache.getreply.GetReplyCacheService;
import eightjbbm.keepgo.util.cache.getreply.GetReplyWorker;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.AiSlot;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// 챗봇 응답 생성 실패 처리 단위 테스트
///
/// AI 호출이 실패하면 예외가 @Async Future에 버려져 폴링이 끝까지 IN_PROGRESS로 남던 문제(2026-10-02)의 회귀 테스트
public class GetReplyFailureUnitTests {

    private static final Long MEMBER_ID = 1L;
    private static final ExtractSlotRequest REQUEST =
            new ExtractSlotRequest("안녕", LocalDate.of(2026, 10, 2), AiSlot.from(null), null);

    private AiServerApiClient aiServerApiClient;
    private GetReplyCacheRepository repository;
    private GetReplyWorker worker;
    private GetReplyCacheService service;

    @BeforeEach
    void setUp() {
        aiServerApiClient = mock(AiServerApiClient.class);
        repository = new GetReplyCacheRepository(new ConcurrentMapCacheManager(GetReplyCacheRepository.cacheName));
        worker = new GetReplyWorker(aiServerApiClient, repository);
        service = new GetReplyCacheService(repository, worker);
        repository.create(MEMBER_ID);
    }

    @Test
    @DisplayName("""
            AI 호출이 실패하면 폴링이 502를 던지고 실패 표시를 지운다
            """)
    void failureIsReportedOnce() {
        when(aiServerApiClient.extractSlot(any())).thenThrow(
                HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable", null, null, null));

        worker.requestGetReply(MEMBER_ID, REQUEST);

        assertThatThrownBy(() -> service.poll(MEMBER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(service.poll(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("""
            처리 중이면 비어 있고, 완료되면 응답을 한 번만 돌려준다
            """)
    void successIsReturnedOnce() {
        assertThat(service.poll(MEMBER_ID)).isEmpty();
        when(aiServerApiClient.extractSlot(any())).thenReturn(new ExtractSlotResponse(
                "extract_success", new ExtractSlotResponse.ExtractData(AiSlot.from(null), "", "안녕하세요")));

        worker.requestGetReply(MEMBER_ID, REQUEST);

        assertThat(service.poll(MEMBER_ID)).get()
                .extracting(ExtractSlotResponse.ExtractData::botMessage)
                .isEqualTo("안녕하세요");
        assertThat(service.poll(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("""
            새 채팅을 만들면 이전 채팅의 꺼내지 않은 결과를 버린다
            """)
    void newChatResetsStaleResult() {
        repository.fail(MEMBER_ID);

        repository.create(MEMBER_ID);

        assertThat(service.poll(MEMBER_ID)).isEmpty();
    }
}
