package eightjbbm.keepgo.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "keepgo.security.jwt")
public record JwtProperties(
        Duration accessTokenTtl,
        String keyId,
        String issuer,
        String audience,
        String clientId,
        Resource publicKey,
        Resource privateKey) {}
