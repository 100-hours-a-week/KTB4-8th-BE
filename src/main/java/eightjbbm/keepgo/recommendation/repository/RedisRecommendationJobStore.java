package eightjbbm.keepgo.recommendation.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recommendation.jobs.backend", havingValue = "redis")
public class RedisRecommendationJobStore implements RecommendationJobStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                    return 0
                end

                redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
                return 1
                """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE = new DefaultRedisScript<>("""
                local value = redis.call('GET', KEYS[1])

                if value and
                   string.sub(value, 1, string.len(ARGV[1])) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end

                return 0
                """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public boolean start(Long memberId, String jobId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(memberId), encode(jobId, State.RUNNING), TTL));
    }

    @Override
    public Optional<Job> find(Long memberId) {
        String value = redis.opsForValue().get(key(memberId));

        if (value == null) {
            return Optional.empty();
        }

        String[] parts = value.split("\\|", 2);

        return Optional.of(new Job(parts[0], State.valueOf(parts[1])));
    }

    @Override
    public boolean finish(Long memberId, String jobId, State state) {
        if (state == State.RUNNING) {
            throw new IllegalArgumentException("종료 상태가 필요합니다.");
        }

        Long result = redis.execute(FINISH, List.of(key(memberId)), encode(jobId, State.RUNNING), encode(jobId, state));

        return Long.valueOf(1).equals(result);
    }

    @Override
    public void remove(Long memberId, String jobId) {
        redis.execute(REMOVE, List.of(key(memberId)), jobId + "|");
    }

    private String key(Long memberId) {
        return "recommendation:v2:job:" + memberId;
    }

    private String encode(String jobId, State state) {
        return jobId + "|" + state.name();
    }
}
