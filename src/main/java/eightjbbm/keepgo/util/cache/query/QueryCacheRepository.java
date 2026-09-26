package eightjbbm.keepgo.util.cache.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QueryCacheRepository {
    private final CacheManager cacheManager;

    private Cache getQueryCache() {
        Cache cache = cacheManager.getCache("query");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
