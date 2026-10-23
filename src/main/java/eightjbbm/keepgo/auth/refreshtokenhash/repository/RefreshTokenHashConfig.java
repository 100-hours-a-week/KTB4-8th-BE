package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.List;

@Configuration
@EnableConfigurationProperties(RefreshTokenHashProperties.class)
public class RefreshTokenHashConfig {
    static final String CACHE_MANAGER_NAME = "refreshTokenHashCacheManager";
    private static final String PROVIDER_NAME_PATH = "keepgo.ephemeral.provider";
    private static final String PROVIDER_NAME_CAFFEINE = "caffeine";
    private static final String PROVIDER_NAME_REDIS = "redis";

    @Bean(CACHE_MANAGER_NAME)
    @ConditionalOnProperty(
            name = PROVIDER_NAME_PATH,
            havingValue = PROVIDER_NAME_CAFFEINE,
            matchIfMissing = true
    )
    public CacheManager caffeineRefreshTokenHashCacheManager(
            RefreshTokenHashProperties properties
    ) {
        var blacklist = new CaffeineCache(
                properties.name(),
                Caffeine.newBuilder()
                        .expireAfterWrite(properties.ttl())
                        .recordStats()
                        .build(),
                false
        );

        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(blacklist));
        return manager;
    }

    @Bean(CACHE_MANAGER_NAME)
    @ConditionalOnProperty(
            name = PROVIDER_NAME_PATH,
            havingValue = PROVIDER_NAME_REDIS
    )
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            RefreshTokenHashProperties properties
    ) {
        var blacklist = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.ttl())
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(properties.name(), blacklist)
                .disableCreateOnMissingCache()
                .build();
    }
}
