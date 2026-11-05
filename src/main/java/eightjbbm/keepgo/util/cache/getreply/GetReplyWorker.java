package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.chat.service.AiServerExtractSlotApiService;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerExtractSlotApiService aiServerApiClient;
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;

    @Async("httpTaskExecutor")
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        ExtractSlotResponseV2 response = aiServerApiClient.extractSlot(request.chat(), request.outingCriteria());
        if (response instanceof ExtractSlotResponseV2.Success successResponse) {
            getReplyCacheRepository.update(memberId, successResponse.data().botMessage());
            OutingCriteria outingCriteria = outingCriteriaRepository
                    .findById(memberId)
                    .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(memberId));
            outingCriteriaRepository.save(outingCriteria.update(
                    successResponse.data().slot().origin(),
                    successResponse.data().slot().region(),
                    successResponse.data().slot().datetime().toInstant(ZoneOffset.of("Asia/Seoul")),
                    Integer.valueOf(successResponse.data().slot().availableTime()),
                    successResponse.data().slot().category(),
                    successResponse.data().query()));
        } else {
            getReplyCacheRepository.setStatusAsFail(memberId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
