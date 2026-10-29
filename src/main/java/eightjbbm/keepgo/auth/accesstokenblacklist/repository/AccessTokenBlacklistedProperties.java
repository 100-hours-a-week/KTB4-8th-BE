package eightjbbm.keepgo.auth.accesstokenblacklist.repository;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.ephemeral.access-token-blacklist")
public record AccessTokenBlacklistedProperties(String name, Duration ttl) {}
