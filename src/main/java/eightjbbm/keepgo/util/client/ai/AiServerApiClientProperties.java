package eightjbbm.keepgo.util.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.clients.ai")
public record AiServerApiClientProperties(
        String address
) {
}
