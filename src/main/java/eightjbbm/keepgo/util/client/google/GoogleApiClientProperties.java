package eightjbbm.keepgo.util.client.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.clients.google")
public record GoogleApiClientProperties(
        String address
) {
}
