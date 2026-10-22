package eightjbbm.keepgo.recommendation.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@ConditionalOnProperty(
        name = "recommendation.jobs.backend",
        havingValue = "caffeine",
        matchIfMissing = true
)
public class CaffeineRecommendationJobStore
        implements RecommendationJobStore {

    private final Cache<Long, Job> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    @Override
    public boolean start(Long memberId, String jobId) {
        return cache.asMap().putIfAbsent(
                memberId,
                new Job(jobId, State.RUNNING)
        ) == null;
    }

    @Override
    public Optional<Job> find(Long memberId) {
        return Optional.ofNullable(cache.getIfPresent(memberId));
    }

    @Override
    public boolean finish(Long memberId, String jobId, State state) {
        if (state == State.RUNNING) {
            throw new IllegalArgumentException("종료 상태가 필요합니다.");
        }

        return cache.asMap().replace(
                memberId,
                new Job(jobId, State.RUNNING),
                new Job(jobId, state)
        );
    }

    @Override
    public void remove(Long memberId, String jobId) {
        cache.asMap().computeIfPresent(
                memberId,
                (key, job) -> job.id().equals(jobId) ? null : job
        );
    }
}
