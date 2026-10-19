package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import eightjbbm.keepgo.auth.accesstokenblacklist.repository.AccessTokenBlacklisted;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
@EnableConfigurationProperties(RefreshTokenHashProperties.class)
public class RefreshTokenHashRepository {

    private final RefreshTokenHashProperties properties;
    private final CacheManager cacheManager;

    public RefreshTokenHashRepository(
            RefreshTokenHashProperties properties,
            @Qualifier(RefreshTokenHashConfig.CACHE_MANAGER_NAME)
            CacheManager cacheManager
    ) {
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    public void save(
            RefreshTokenHash refreshTokenHash
    ) {
        if (refreshTokenHash.isExpired()) return;
        cache().put(
                refreshTokenHash.hash(),
                refreshTokenHash);
    }

    public Optional<RefreshTokenHash> findById(String hash) {
        return Optional.ofNullable(cache().get(hash, RefreshTokenHash.class))
                .filter(refreshTokenHash ->
                        !refreshTokenHash.isExpired());
    }

    private Cache cache() {
        return Objects.requireNonNull(
                cacheManager.getCache(properties.name()));
    }
}
