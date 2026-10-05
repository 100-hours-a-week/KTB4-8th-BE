package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerApiClient aiServerApiClient;
    private final GetReplyCacheRepository getReplyCacheRepository;

    @Async("httpTaskExecutor")
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        ExtractSlotResponse response = aiServerApiClient.extractSlot(request);
        if (response instanceof ExtractSlotResponse.Success successResponse) {
            getReplyCacheRepository.update(memberId, successResponse.data());
        } else {
            throw new ServiceUnavailableException();
        }
        return CompletableFuture.completedFuture(null);
    }
}
