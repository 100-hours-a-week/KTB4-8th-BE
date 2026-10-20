package eightjbbm.keepgo.chat.service.requeststore;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "keepgo.ephemeral.provider",
        havingValue = "redis"
)
public class RedisChatRequestStore implements ChatRequestStore {

    private static final DefaultRedisScript<Long> RELEASE =
            new DefaultRedisScript<>("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public boolean acquire(Long memberId, String requestId) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key(memberId), requestId));
    }

    @Override
    public void release(Long memberId, String requestId) {
        redis.execute(RELEASE, List.of(key(memberId)), requestId);
    }

    private String key(Long memberId) {
        return "chat:v2:request:" + memberId;
    }
}
