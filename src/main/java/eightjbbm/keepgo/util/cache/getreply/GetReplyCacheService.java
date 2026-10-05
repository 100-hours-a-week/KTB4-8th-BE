package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import eightjbbm.keepgo.util.exception.ConflictException;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class GetReplyCacheService {
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final GetReplyWorker worker;

    public void createRequest(GetReplyRequest request) {
        if (getReplyCacheRepository.exists(request.memberId())) {
            throw new ConflictException();
        }
        getReplyCacheRepository.create(request.memberId());
        worker.requestGetReply(
                request.memberId(),
                ExtractSlotRequest.from(request)
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
