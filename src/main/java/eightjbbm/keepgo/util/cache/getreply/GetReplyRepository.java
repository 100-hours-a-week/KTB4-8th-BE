package eightjbbm.keepgo.util.cache.getreply;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GetReplyRepository {
    private final CacheManager cacheManager;

    public void create(Long memberId) {
        getReplyCache().putIfAbsent(memberId, null);
    }

    public Optional<String> poll(Long memberId) {
        Optional<String> content = Optional.ofNullable(getReplyCache().get(memberId, String.class));
        if (content.isPresent()) {
            evict(memberId);
        }
        return content;
    }

    public void update(Long memberId, String content) {
        getReplyCache().put(memberId, content);
    }

    private void evict(Long memberId) {
        getReplyCache().evictIfPresent(memberId);
    }

    private Cache getReplyCache() {
        Cache cache = cacheManager.getCache("replyJob");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
