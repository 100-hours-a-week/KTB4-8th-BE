package eightjbbm.keepgo.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.security.jwt")
public record JwtProperties(
        Duration accessTokenTtl,
        String keyId,
        String issuer,
        String audience,
        String clientId,
        String publicKey,
        String privateKey) {}
