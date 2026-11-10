package eightjbbm.keepgo.util.client.ai.sse;

import eightjbbm.keepgo.util.client.ai.AiServerApiClientProperties;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({AiServerApiClientProperties.class, AiCallProperties.class})
public class AiSseClientConfig {
    @Bean(destroyMethod = "close")
    AiSseClient aiSseClient(
            AiServerApiClientProperties properties, AiCallProperties callProperties, ObjectMapper mapper) {
        return new AiSseClient(
                URI.create(properties.address()),
                mapper,
                callProperties.connectTimeout(),
                callProperties.streamIdleTimeout(),
                callProperties.analysisTimeout());
    }
}
