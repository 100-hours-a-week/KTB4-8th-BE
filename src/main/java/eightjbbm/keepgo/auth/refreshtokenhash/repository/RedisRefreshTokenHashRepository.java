package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

@Repository
@ConditionalOnProperty(
        name = "keepgo.ephemeral.provider",
        havingValue = "redis"
)
public class RedisRefreshTokenHashRepository
        implements RefreshTokenHashRepository {

    private static final byte[] CONSUME_SCRIPT = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end

            local time = redis.call('TIME')
            local nowMillis =
                tonumber(time[1]) * 1000
                + math.floor(tonumber(time[2]) / 1000)

            if nowMillis >= tonumber(ARGV[2]) then
                return 0
            end

            return redis.call('DEL', KEYS[1])
            """.getBytes(StandardCharsets.UTF_8);

    private final RedisCache cache;
    private final RedisConnectionFactory connectionFactory;

    public RedisRefreshTokenHashRepository(
            RefreshTokenHashProperties properties,
            @Qualifier(RefreshTokenHashConfig.CACHE_MANAGER_NAME)
            CacheManager cacheManager,
            RedisConnectionFactory connectionFactory
    ) {
        this.cache = (RedisCache) Objects.requireNonNull(
                cacheManager.getCache(properties.name()),
                "refresh token 캐시가 설정되지 않았습니다."
        );
        this.connectionFactory = connectionFactory;
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
        byte[] key = serializeKey(hash);

        try (var connection = connectionFactory.getConnection()) {
            byte[] stored = connection.stringCommands().get(key);

            if (stored == null) {
                return Optional.empty();
            }

            Object value = cache.getCacheConfiguration()
                    .getValueSerializationPair()
                    .read(ByteBuffer.wrap(stored));

            if (!(value instanceof RefreshTokenHash current)
                    || current.state() != RefreshTokenState.ACTIVE
                    || current.isExpired()) {
                return Optional.empty();
            }

            Long result = connection.scriptingCommands().eval(
                    CONSUME_SCRIPT,
                    ReturnType.INTEGER,
                    1,
                    key,
                    stored,
                    Long.toString(current.expiredAt().toEpochMilli())
                            .getBytes(StandardCharsets.UTF_8)
            );

            return Long.valueOf(1).equals(result)
                    ? Optional.of(current)
                    : Optional.empty();
        }
    }

    private byte[] serializeKey(String hash) {
        var configuration = cache.getCacheConfiguration();

        String key = configuration.usePrefix()
                ? configuration.getKeyPrefixFor(cache.getName()) + hash
                : hash;

        return toBytes(
                configuration.getKeySerializationPair().write(key)
        );
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] result = new byte[copy.remaining()];
        copy.get(result);
        return result;
    }
}