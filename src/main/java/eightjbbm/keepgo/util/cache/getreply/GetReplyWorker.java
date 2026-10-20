package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerApiClient aiServerApiClient;
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;

    @Async("httpTaskExecutor")
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        ExtractSlotResponse response = aiServerApiClient.extractSlot(request);
        if (response instanceof ExtractSlotResponse.Success successResponse) {
            getReplyCacheRepository.update(memberId, successResponse.data().botMessage());
            OutingCriteria outingCriteria = outingCriteriaRepository
                    .findById(memberId)
                    .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(memberId));
            outingCriteriaRepository.save(
                    outingCriteria.update(
                            successResponse.data().slot().origin(),
                            successResponse.data().slot().region(),
                            successResponse.data().slot().datetime().atStartOfDay().toInstant(ZoneOffset.UTC),
                            successResponse.data().slot().availableTime(),
                            Set.of(successResponse.data().slot().category()),
                            successResponse.data().query()));
        } else {
            getReplyCacheRepository.setStatusAsFail(memberId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
