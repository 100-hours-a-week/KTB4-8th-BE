package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(
        prefix = "keepgo.ephemeral.refresh-token-hash"
)
public record RefreshTokenHashProperties(
        String name,
        Duration ttl
) {
}
