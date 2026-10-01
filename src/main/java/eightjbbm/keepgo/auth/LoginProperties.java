package eightjbbm.keepgo.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.security")
public record LoginProperties(
        String redirectionAddress
) {
}
