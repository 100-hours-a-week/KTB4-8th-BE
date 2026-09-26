package eightjbbm.keepgo.auth.rt;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RtHashCacheRepository {
    private final CacheManager cacheManager;

    public void create(String rtHash, RtHashCacheValue rtHashCacheValue) {
        getRtHashCache().putIfAbsent(rtHash, rtHashCacheValue);
    }

    public Optional<RtHashCacheValue> read(String rtHash) {
        return Optional.ofNullable(getRtHashCache().get(rtHash, RtHashCacheValue.class));
    }

    public void update(String rtHash, RtHashCacheValue rtHashCacheValue) {
        getRtHashCache().put(rtHash, rtHashCacheValue);
    }

    public void delete(String rtHash) {
        getRtHashCache().evict(rtHash);
    }

    private Cache getRtHashCache() {
        Cache cache = cacheManager.getCache("rtHash");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
