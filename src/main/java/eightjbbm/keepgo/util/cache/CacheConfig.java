package eightjbbm.keepgo.util.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@EnableCaching
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RtHashCacheProperties.class)
public class CacheConfig {

    private final RtHashCacheProperties rtHashCacheProperties;

    @Bean
    public CacheManager cacheManager() {
        var cacheManager = new SimpleCacheManager();
        var replyJob = new CaffeineCache(
                "replyJob",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build()
        );

        var recommendationJob = new CaffeineCache(
                "recommendationJob",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build()
        );

        var slot = new CaffeineCache(
                "slot",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build()
        );

        // 슬롯과 같은 대화 단위라 TTL도 맞춘다.
        var query = new CaffeineCache(
                "query",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build()
        );

        var atBlacklist = new CaffeineCache(
                "atBlacklist",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build()
        );

        var rtHash = new CaffeineCache(
                "rtHash",
                Caffeine.newBuilder()
                        .maximumSize(rtHashCacheProperties.maximumSize())
                        .expireAfterWrite(rtHashCacheProperties.expireAfterWrite())
                        .recordStats()
                        .build()
        );

        cacheManager.setCaches(List.of(replyJob, recommendationJob, slot, query, atBlacklist, rtHash));
        return cacheManager;
    }
}
