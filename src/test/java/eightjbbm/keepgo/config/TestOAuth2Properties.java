package eightjbbm.keepgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.google")
public record TestOAuth2Properties(
        String clientId,
        String clientSecret,
        List<String> scope
) {}