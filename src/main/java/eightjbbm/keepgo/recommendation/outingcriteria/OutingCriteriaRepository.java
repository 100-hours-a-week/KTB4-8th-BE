package eightjbbm.keepgo.recommendation.outingcriteria;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
@EnableConfigurationProperties(OutingCriteriaProperties.class)
public class OutingCriteriaRepository {

    private final OutingCriteriaProperties properties;
    private final CacheManager cacheManager;

    public OutingCriteriaRepository(
            OutingCriteriaProperties properties,
            @Qualifier(OutingCriteriaConfig.CACHE_MANAGER_NAME)
            CacheManager cacheManager) {
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    public void save(OutingCriteria outingCriteria) {
        cache().put(outingCriteria.getMemberId(), outingCriteria);
    }

    public Optional<OutingCriteria> findById(Long memberId) {
        return Optional.ofNullable(cache().get(memberId, OutingCriteria.class));
    }

    public void deleteById(Long memberId) {
        cache().evictIfPresent(memberId);
    }

    private Cache cache() {
        return Objects.requireNonNull(
                cacheManager.getCache(properties.name()));
    }
}
