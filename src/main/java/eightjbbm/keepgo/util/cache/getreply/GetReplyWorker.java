package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.chat.service.AiServerExtractSlotApiService;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetReplyWorker {

    private final AiServerExtractSlotApiService aiServerApiClient;
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;

    @Async("httpTaskExecutor")
    public CompletableFuture<Void> requestGetReply(Long memberId, ExtractSlotRequest request) {
        try {
            ExtractSlotResponseV2 data =
                    aiServerApiClient.extractSlot(request.chat(), request.outingCriteria(), text -> {});
            if (data == null || data.slot() == null || data.botMessage() == null) {
                getReplyCacheRepository.setStatusAsFail(memberId);
                return CompletableFuture.completedFuture(null);
            }
            var slot = data.slot();
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
            getReplyCacheRepository.update(memberId, data.botMessage());
        } catch (RuntimeException error) {
            log.warn("응답 캐시 작업 실패: memberId={}", memberId, error);
            getReplyCacheRepository.setStatusAsFail(memberId);
        }
        return CompletableFuture.completedFuture(null);
    }
}
