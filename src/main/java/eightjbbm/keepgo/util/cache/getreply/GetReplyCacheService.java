package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetReplyCacheService {
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final GetReplyWorker worker;

    public void createRequest(GetReplyRequest request) {
        getReplyCacheRepository.create(request.memberId());
        worker.requestGetReply(
                request.memberId(),
                ExtractSlotRequest.from(request)
        );
    }

    public Optional<String> poll(Long memberId) {
        return getReplyCacheRepository.poll(memberId);
    }
}
