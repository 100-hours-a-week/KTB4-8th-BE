package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Repository
@ConditionalOnProperty(
        name = "keepgo.ephemeral.provider",
        havingValue = "caffeine",
        matchIfMissing = true
)
public class CaffeineRefreshTokenHashRepository
        implements RefreshTokenHashRepository {

    private final CaffeineCache cache;

    public CaffeineRefreshTokenHashRepository(
            RefreshTokenHashProperties properties,
            @Qualifier(RefreshTokenHashConfig.CACHE_MANAGER_NAME)
            CacheManager cacheManager
    ) {
        this.cache = (CaffeineCache) Objects.requireNonNull(
                cacheManager.getCache(properties.name()),
                "refresh token 캐시가 설정되지 않았습니다."
        );
    }

    @Override
    public void save(RefreshTokenHash token) {
        if (token.isExpired()) {
            return;
        }

        cache.put(token.hash(), token);
    }

    @Override
    public Optional<RefreshTokenHash> findById(String hash) {
        return Optional.ofNullable(
                cache.get(hash, RefreshTokenHash.class)
        ).filter(token -> !token.isExpired());
    }

    @Override
    public Optional<RefreshTokenHash> consumeActive(String hash) {
        var consumed = new AtomicReference<RefreshTokenHash>();

        cache.getNativeCache().asMap().computeIfPresent(
                hash,
                (key, value) -> {
                    RefreshTokenHash current = (RefreshTokenHash) value;

                    if (current.state() == RefreshTokenState.ACTIVE
                            && !current.isExpired()) {
                        consumed.set(current);

                        // computeIfPresent에서 null 반환은 해당 항목 삭제다.
                        return null;
                    }

                    return current;
                }
        );

        return Optional.ofNullable(consumed.get());
    }
}