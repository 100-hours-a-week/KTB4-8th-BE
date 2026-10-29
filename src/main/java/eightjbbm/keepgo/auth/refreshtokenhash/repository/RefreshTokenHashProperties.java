package eightjbbm.keepgo.auth.refreshtokenhash.repository;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.ephemeral.refresh-token-hash")
public record RefreshTokenHashProperties(String name, Duration ttl) {}
