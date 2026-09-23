package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.AiServerClient;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerClient aiServerClient;
    private final GetReplyRepository getReplyRepository;

    @Async("httpTaskExecutor")
    @Transactional
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        ExtractSlotResponse response = aiServerClient.extractSlot(request);
        getReplyRepository.update(memberId, response.data().botMessage());
        return CompletableFuture.completedFuture(null);
    }
}
