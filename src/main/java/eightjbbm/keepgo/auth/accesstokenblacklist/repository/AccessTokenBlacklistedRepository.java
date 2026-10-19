package eightjbbm.keepgo.auth.accesstokenblacklist.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
@EnableConfigurationProperties(AccessTokenBlacklistedProperties.class)
public class AccessTokenBlacklistedRepository {

    private final AccessTokenBlacklistedProperties properties;
    private final CacheManager cacheManager;

    public AccessTokenBlacklistedRepository(
            AccessTokenBlacklistedProperties properties,
            @Qualifier(AccessTokenBlacklistedConfig.CACHE_MANAGER_NAME)
            CacheManager cacheManager
    ) {
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    public void save(
            AccessTokenBlacklisted accessTokenBlacklisted
    ) {
        if (accessTokenBlacklisted.isExpired()) return;
        cache().put(
                accessTokenBlacklisted.jti(),
                accessTokenBlacklisted);
    }

    public boolean existsById(String jti) {
        return Optional.ofNullable(cache().get(jti, AccessTokenBlacklisted.class))
                .filter(accessTokenBlacklisted ->
                        !accessTokenBlacklisted.isExpired())
                .isPresent();
    }

    private Cache cache() {
        return Objects.requireNonNull(
                cacheManager.getCache(properties.name()));
    }
}
