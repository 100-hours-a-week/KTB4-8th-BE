package eightjbbm.keepgo.util.cache.atblacklist;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AtBlacklistCacheRepository {
    private final CacheManager cacheManager;

    public void create(String jti) {
        getAtBlacklist().putIfAbsent(jti, true);
    }

    public Optional<Boolean> read(String jti) {
        return Optional.ofNullable(getAtBlacklist().get(jti, Boolean.class));
    }

    private Cache getAtBlacklist() {
        Cache cache = cacheManager.getCache("atBlacklist");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
