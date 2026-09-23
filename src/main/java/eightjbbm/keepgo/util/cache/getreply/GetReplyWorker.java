package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.AiServerClient;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerClient aiServerClient;

    @Async("httpTaskExecutor")
    @Transactional
    public CompletableFuture<Void> getReply(ExtractSlotRequest request) {
        // 작업 상태를 진행 중으로 하여 캐시에 적재
        aiServerClient.extractSlot(request);
        // 완료 시 적재된 캐시에 반환된 응답 기록
        return CompletableFuture.completedFuture(null);
    }
}
