package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.exception.ConflictException;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class GetReplyCacheService {
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final GetReplyWorker worker;

    public void createRequest(Long memberId, String content, LocalDate createdDate, OutingCriteria outingCriteria) {
        if (getReplyCacheRepository.exists(memberId)) {
            throw new ConflictException();
        }
        getReplyCacheRepository.create(memberId);
        worker.requestGetReply(
                memberId,
                new ExtractSlotRequest(content, createdDate, outingCriteria)
        ).orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    if (error instanceof TimeoutException) {
                        throw new ServiceUnavailableException();
                    }
                    return null;
                });
    }

    public GetReplyJobStatus checkStatus(Long memberId) {
        return getReplyCacheRepository.getStatus(memberId);
    }

    public String getReply(Long memberId) {
        return getReplyCacheRepository.getReply(memberId).get();
    }
}
