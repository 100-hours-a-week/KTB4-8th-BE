package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerApiClient aiServerApiClient;
    private final GetReplyCacheRepository getReplyCacheRepository;

    /// 반환한 CompletableFuture를 아무도 확인하지 않으므로, 예외는 여기서 잡아 로그와 실패 표시로 남긴다.
    /// 잡지 않으면 예외가 Future 안에 버려지고 폴링은 끝까지 IN_PROGRESS로 남는다.
    @Async("httpTaskExecutor")
    @Transactional
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        try {
            ExtractSlotResponse response = aiServerApiClient.extractSlot(request);
            if (response == null || response.data() == null) {
                throw new IllegalStateException("AI 슬롯 추출 응답이 비어 있다");
            }
            getReplyCacheRepository.update(memberId, response.data());
        } catch (Exception e) {
            log.error("AI 슬롯 추출 실패 memberId={}", memberId, e);
            getReplyCacheRepository.fail(memberId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
