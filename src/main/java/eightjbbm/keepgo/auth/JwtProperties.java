package eightjbbm.keepgo.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "keepgo.security.jwt")
public record JwtProperties(
        Duration accessTokenTtl,
        String keyId,
        String issuer,
        String audience,
        String clientId,
        String publicKey,
        String privateKey
) {
}
