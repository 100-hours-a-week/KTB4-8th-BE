package eightjbbm.keepgo.util.cache.recommendationjob;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecommendationJobCacheRepository {
    private final CacheManager cacheManager;

    public void create(Long memberId, Long jobId) {
        getRecommendationJobCache().putIfAbsent(memberId, jobId);
    }

    public Optional<Long> read(Long memberId) {
        return Optional.ofNullable(getRecommendationJobCache().get(memberId, Long.class));
    }

    public void delete(Long memberId) {
        getRecommendationJobCache().evict(memberId);
    }


    private Cache getRecommendationJobCache() {
        Cache cache = cacheManager.getCache("recommendationJob");

        if (cache == null) {
            throw new IllegalStateException("No Cache");
        }

        return cache;
    }
}
