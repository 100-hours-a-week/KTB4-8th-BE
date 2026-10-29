package eightjbbm.keepgo.recommendation.outingcriteria;

import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

@Repository
public class OutingCriteriaRepository {

    private final CacheManager cacheManager;

    public OutingCriteriaRepository(@Qualifier(OutingCriteriaConfig.CACHE_MANAGER_NAME) CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Optional<OutingCriteria> findById(Long memberId) {
        return Optional.ofNullable(cache().get(memberId, OutingCriteria.class));
    }

    public void save(OutingCriteria criteria) {
        cache().put(criteria.getMemberId(), criteria);
    }

    public void deleteById(Long memberId) {
        cache().evictIfPresent(memberId);
    }

    private Cache cache() {
        return Objects.requireNonNull(cacheManager.getCache(OutingCriteriaConfig.CACHE_NAME));
    }
}
