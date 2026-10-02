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

    /// 챗봇 응답 생성에 실패했음을 나타내는 값
    public enum Failure { INSTANCE }

    public void create(Long memberId) {
        getReplyCache().putIfAbsent(memberId, null);
    }

    /// 완료된 결과({@link ExtractSlotResponse.ExtractData} 또는 {@link Failure})를 꺼내고 지운다.
    /// 아직 처리 중이면 비어 있다.
    public Optional<Object> poll(Long memberId) {
        Cache.ValueWrapper wrapper = getReplyCache().get(memberId);
        Optional<Object> content = Optional.ofNullable(wrapper == null ? null : wrapper.get());
        if (content.isPresent()) {
            evict(memberId);
        }
        return content;
    }

    public void update(Long memberId, ExtractSlotResponse.ExtractData content) {
        getReplyCache().put(memberId, content);
    }

    public void fail(Long memberId) {
        getReplyCache().put(memberId, Failure.INSTANCE);
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
