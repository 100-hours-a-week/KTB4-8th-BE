package eightjbbm.keepgo.util.client.geocoding;

import eightjbbm.keepgo.util.client.ai.AiServerApiClientProperties;
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
public class GeoCodingApiClientConfig {

    private final GeoCodingApiClientProperties properties;

    @Bean
    RestClient geoCodingRestClient(
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
