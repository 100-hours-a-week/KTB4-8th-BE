package eightjbbm.keepgo.util.client.ai.job;

import eightjbbm.keepgo.util.client.ai.AiServerApiClientProperties;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import eightjbbm.keepgo.util.client.ai.sse.AiSseClient;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AiServerApiClientProperties.class)
public class AiJobClientConfig {
    @Bean(destroyMethod = "close")
    VideoAnalysisJobClient videoAnalysisJobClient(AiServerApiClientProperties properties, ObjectMapper mapper) {
        return new VideoAnalysisJobClient(URI.create(properties.address()).resolve("/v1/analyze-video"), mapper);
    }

    @Bean(destroyMethod = "close")
    AiSseClient aiSseClient(
            AiServerApiClientProperties properties, AiCallProperties callProperties, ObjectMapper mapper) {
        return new AiSseClient(
                URI.create(properties.address()),
                mapper,
                callProperties.connectTimeout(),
                callProperties.analysisTimeout());
    }
}
