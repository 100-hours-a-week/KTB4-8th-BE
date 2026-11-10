package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.chat.service.AiServerExtractSlotApiService;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamException;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import java.time.ZoneId;
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
        try {
            var data = aiServerApiClient.extractSlot(request.chat(), request.outingCriteria(), text -> {});
            if (data.slot() == null || data.botMessage() == null) {
                getReplyCacheRepository.setStatusAsFail(memberId);
                return CompletableFuture.completedFuture(null);
            }
            var slot = data.slot();
            getReplyCacheRepository.update(memberId, data.botMessage());
            OutingCriteria outingCriteria = outingCriteriaRepository
                    .findById(memberId)
                    .orElse(OutingCriteria.emptyOutingCriteriaWithMemberId(memberId));
            outingCriteriaRepository.save(outingCriteria.update(
                    slot.origin(),
                    slot.region(),
                    slot.datetime() == null
                            ? null
                            : slot.datetime().atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                    slot.availableTime() == null
                            ? null
                            : Integer.valueOf(slot.availableTime().trim()),
                    slot.category(),
                    data.query()));
        } catch (AiStreamException error) {
            getReplyCacheRepository.setStatusAsFail(memberId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
