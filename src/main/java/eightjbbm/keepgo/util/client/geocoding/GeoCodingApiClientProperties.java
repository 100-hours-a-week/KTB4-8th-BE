package eightjbbm.keepgo.util.client.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.clients.geocoding")
public record GeoCodingApiClientProperties(
        String address,
        String key
) {
}
