package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetReplyCacheService {
    private final CacheManager cacheManager;
    private final GetReplyWorker worker;

    public void createRequest(Long memberId, ExtractSlotRequest request) {
        //같은 userId로 요청된 기록이 있는 지 검증
        getValue(memberId);
        worker.getReply(request);
    }

    public Optional<String> poll(Long memberId) {
        Optional<String> content = Optional.ofNullable(getValue(memberId).getReply());
        if (content.isPresent()) {
            getReplyJobCache().evictIfPresent(memberId);
        }
        return content;
    }

    private Cache getReplyJobCache() {
        Cache cache = cacheManager.getCache("replyJob");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }

    public GetReplyValue getValue(Long memberId) {
        return Optional.ofNullable(getReplyJobCache().get(memberId, GetReplyValue.class)).orElseThrow();
    }
}
