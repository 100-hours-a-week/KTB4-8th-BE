package eightjbbm.keepgo.auth.authorizationrequest;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keepgo.security.oauth2.authorization-request.provider", havingValue = "redis")
public class RedisAuthorizationRequestStore implements AuthorizationRequestStore {
    private static final String PREFIX = "oauth:authorization-request:";
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return redis.error_reply('Duplicate authorization request') end
            redis.call('HSET', KEYS[1], 'json', ARGV[1], 'browser', ARGV[2], 'expires', ARGV[3])
            redis.call('PEXPIREAT', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final String CHECK = """
            if redis.call('HGET', KEYS[1], 'browser') ~= ARGV[1] then return nil end
            local expires = redis.call('HGET', KEYS[1], 'expires')
            if not expires or tonumber(expires) <= tonumber(ARGV[2]) then return nil end
            local json = redis.call('HGET', KEYS[1], 'json')
            """;
    private static final DefaultRedisScript<String> FIND =
            new DefaultRedisScript<>(CHECK + "return json", String.class);
    private static final DefaultRedisScript<String> CONSUME =
            new DefaultRedisScript<>(CHECK + "redis.call('DEL', KEYS[1])\nreturn json", String.class);
    private final StringRedisTemplate redis;

    @Override
    public void save(String id, String json, String browserHash, Instant expiresAt) {
        redis.execute(SAVE, List.of(PREFIX + id), json, browserHash, Long.toString(expiresAt.toEpochMilli()));
    }

    @Override
    public String find(String id, String browserHash, Instant now) {
        return redis.execute(FIND, List.of(PREFIX + id), browserHash, Long.toString(now.toEpochMilli()));
    }

    @Override
    public String consume(String id, String browserHash, Instant now) {
        return redis.execute(CONSUME, List.of(PREFIX + id), browserHash, Long.toString(now.toEpochMilli()));
    }
}
