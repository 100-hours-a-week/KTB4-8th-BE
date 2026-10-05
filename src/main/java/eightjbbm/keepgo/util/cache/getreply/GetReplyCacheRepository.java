package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GetReplyCacheRepository {
    public static final String cacheName = "replyJob";
    private final CacheManager cacheManager;

    public void create(Long memberId) {
        getReplyCache().putIfAbsent(memberId, GetReplyValue.emptyValue());
    }

    public Optional<ExtractSlotResponse.Success.ExtractData> poll(Long memberId) {
        Optional<ExtractSlotResponse.Success.ExtractData> content = Optional.ofNullable(getReplyCache().get(memberId, ExtractSlotResponse.Success.ExtractData.class));
        if (content.isPresent()) {
            evict(memberId);
        }
        return content;
    }

    public GetReplyJobStatus getStatus(Long memberId) {
        return getReplyCache().get(memberId, GetReplyValue.class).getStatus();
    }

    public Optional<String> getReply(Long memberId) {
        return Optional.ofNullable(getReplyCache().get(memberId, GetReplyValue.class).getReply());
    }

    public void setStatusAsFail(Long memberId) {
        getReplyCache().get(memberId, GetReplyValue.class).setStatusAsFail();
    }

    public void update(Long memberId, String content) {
        getReplyCache().put(memberId, content);
    }

    private void evict(Long memberId) {
        getReplyCache().evictIfPresent(memberId);
    }

    private Cache getReplyCache() {
        Cache cache = cacheManager.getCache(cacheName);

        if (cache == null) {
            throw new IllegalStateException("No Cache: " + cacheName);
        }

        return cache;
    }
}
