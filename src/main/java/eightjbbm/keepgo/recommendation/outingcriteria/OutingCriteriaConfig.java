package eightjbbm.keepgo.recommendation.outingcriteria;

import com.github.benmanes.caffeine.cache.Caffeine;
import eightjbbm.keepgo.util.Coordinate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class OutingCriteriaConfig {

    static final String CACHE_MANAGER_NAME = "outingCriteriaCacheManager";
    static final String CACHE_NAME = "outing-criteria";

    @Bean(CACHE_MANAGER_NAME)
    @ConditionalOnProperty(name = "keepgo.ephemeral.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        var manager = new SimpleCacheManager();

        manager.setCaches(
                List.of(new CaffeineCache(CACHE_NAME, Caffeine.newBuilder().build(), false)));

        return manager;
    }

    @Bean(CACHE_MANAGER_NAME)
    @ConditionalOnProperty(name = "keepgo.ephemeral.provider", havingValue = "redis")
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory, tools.jackson.databind.ObjectMapper mapper) {
        RedisSerializer<OutingCriteria> serializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(OutingCriteria value) {
                return value == null ? null : mapper.writeValueAsBytes(CriteriaData.from(value));
            }

            @Override
            public OutingCriteria deserialize(byte[] bytes) {
                return bytes == null || bytes.length == 0
                        ? null
                        : mapper.readValue(bytes, CriteriaData.class).toCriteria();
            }
        };

        var configuration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CACHE_NAME, configuration)
                .disableCreateOnMissingCache()
                .build();
    }

    record CriteriaData(
            Long memberId,
            Coordinate coordinate,
            String location,
            Instant datetime,
            Integer minutes,
            Set<String> categories,
            String preference) {
        static CriteriaData from(OutingCriteria value) {
            return new CriteriaData(
                    value.getMemberId(),
                    value.getUserCoordinate(),
                    value.getRequestedLocationName(),
                    value.getRequestedDatetime(),
                    value.getUserAvailableTimeMinutes(),
                    value.getCategories(),
                    value.getUserPreferenceSummary());
        }

        OutingCriteria toCriteria() {
            return new OutingCriteria(memberId, coordinate, location, datetime, minutes, categories, preference);
        }
    }
}
