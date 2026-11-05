package eightjbbm.keepgo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.google")
public record TestOAuth2Properties(String clientId, String clientSecret, List<String> scope) {}
