package eightjbbm.keepgo.util.client.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AiServerApiClientProperties.class)
public class AiServerApiClientConfig {

    private final AiServerApiClientProperties properties;

    @Bean
    RestClient aiServerRestClient(
            RestClient.Builder builder
    ) {
        return builder
                .baseUrl(properties.address())
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}
