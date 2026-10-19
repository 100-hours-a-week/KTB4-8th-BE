package eightjbbm.keepgo.auth.accesstokenblacklist.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(
        prefix = "keepgo.ephemeral.access-token-blacklist"
)
public record AccessTokenBlacklistedProperties(
        String name,
        Duration ttl
) {
}
