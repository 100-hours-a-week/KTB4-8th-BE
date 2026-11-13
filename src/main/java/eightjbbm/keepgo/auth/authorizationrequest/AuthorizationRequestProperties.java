package eightjbbm.keepgo.auth.authorizationrequest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("keepgo.security.oauth2.authorization-request")
public record AuthorizationRequestProperties(
        @DefaultValue("rdb") String provider,
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("true") boolean secureCookie,
        @DefaultValue("http://localhost:3000/login") String failureUrl) {
    public AuthorizationRequestProperties {
        if (!"rdb".equals(provider)) {
            throw new IllegalArgumentException("Unsupported OAuth authorization request provider");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("OAuth authorization request TTL must be positive");
        }
    }
}
