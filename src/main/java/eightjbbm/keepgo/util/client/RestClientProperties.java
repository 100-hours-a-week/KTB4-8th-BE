package eightjbbm.keepgo.util.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.clients")
public record RestClientProperties(
        String google,
        String geocoding,
        String ai
) {
}
