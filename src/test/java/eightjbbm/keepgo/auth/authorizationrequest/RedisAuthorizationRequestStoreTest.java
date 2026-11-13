package eightjbbm.keepgo.auth.authorizationrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@DisplayName("Redis OAuth \uc778\uc99d \uc694\uccad \uc800\uc7a5\uc18c")
@EnabledIfEnvironmentVariable(named = "OAUTH_REDIS_TEST_PORT", matches = "[0-9]+")
class RedisAuthorizationRequestStoreTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static RedisAuthorizationRequestStore store;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("OAUTH_REDIS_TEST_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        store = new RedisAuthorizationRequestStore(redis);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Test
    @DisplayName("\ub3d9\uc2dc \uc694\uccad\uc744 \ud55c \ubc88\ub9cc \uc18c\ube44\ud55c\ub2e4")
    void consumesOnce() throws Exception {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        store.save(id, "payload", "browser", now.plusSeconds(60));
        assertThatThrownBy(() -> store.save(id, "replacement", "other", now.plusSeconds(60)))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.consume(id, "wrong", now)).isNull();
        assertThat(store.find(id, "browser", now)).isEqualTo("payload");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                gate.await();
                return store.consume(id, "browser", now);
            });
            var second = executor.submit(() -> {
                gate.await();
                return store.consume(id, "browser", now);
            });
            gate.countDown();
            assertThat(java.util.Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder("payload", null);
        }
        assertThat(store.find(id, "browser", now)).isNull();
    }

    @Test
    @DisplayName("\ub9cc\ub8cc\ub41c \uc694\uccad\uc740 \uac70\ubd80\ud558\uace0 \uc0ad\uc81c\ud55c\ub2e4")
    void expires() {
        String id = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(60);
        store.save(id, "payload", "browser", expires);
        assertThat(store.consume(id, "browser", expires)).isNull();
        String key = "oauth:authorization-request:" + id;
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
        redis.expireAt(key, Instant.now().minusSeconds(1));
        assertThat(redis.hasKey(key)).isFalse();
    }
}
