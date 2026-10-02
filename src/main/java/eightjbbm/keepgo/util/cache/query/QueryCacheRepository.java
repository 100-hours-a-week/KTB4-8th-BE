package eightjbbm.keepgo.util.cache.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/// 회원의 대화에서 AI가 정리한 정성 조건(query) 저장용 데이터
///
/// key: Long memberId
///
/// value: String query
@Repository
@RequiredArgsConstructor
public class QueryCacheRepository {
    public static final String cacheName = "query";
    private final CacheManager cacheManager;

    public Optional<String> read(Long memberId) {
        return Optional.ofNullable(getQueryCache().get(memberId, String.class));
    }

    public void update(Long memberId, String query) {
        getQueryCache().put(memberId, query);
    }

    public void delete(Long memberId) {
        getQueryCache().evictIfPresent(memberId);
    }

    private Cache getQueryCache() {
        Cache cache = cacheManager.getCache(cacheName);

        if (cache == null) {
            throw new IllegalStateException("No Cache: " + cacheName);
        }

        return cache;
    }
}
