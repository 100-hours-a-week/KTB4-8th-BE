package eightjbbm.keepgo.util.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "keepgo.cache.rt-hash")
public record RtHashCacheProperties(
        Long maximumSize,
        Duration expireAfterWrite
) {
}
