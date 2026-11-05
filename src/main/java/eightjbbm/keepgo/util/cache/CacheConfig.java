package eightjbbm.keepgo.util.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var cacheManager = new SimpleCacheManager();
        var replyJob = new CaffeineCache(
                "replyJob",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build());

        var recommendationJob = new CaffeineCache(
                "recommendationJob",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build());

        var slot = new CaffeineCache(
                "slot",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build());

        var atBlacklist = new CaffeineCache(
                "atBlacklist",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofSeconds(600))
                        .recordStats()
                        .build());

        cacheManager.setCaches(List.of(replyJob, recommendationJob, slot, atBlacklist));
        return cacheManager;
    }
}
