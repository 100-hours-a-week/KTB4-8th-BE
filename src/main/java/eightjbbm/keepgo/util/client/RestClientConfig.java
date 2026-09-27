package eightjbbm.keepgo.util.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RestClientProperties.class)
public class RestClientConfig {

    private final RestClientProperties restClientProperties;

    @Bean
    RestClient googleRestClient(
        RestClient.Builder builder
    ) {
        return builder
                .baseUrl(restClientProperties.google())
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    @Bean
    RestClient geoCodingRestClient(
        RestClient.Builder builder
    ) {
        return builder
                .baseUrl(restClientProperties.geocoding())
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    @Bean
    RestClient aiServerRestClient(
            RestClient.Builder builder
    ) {
        return builder
                .baseUrl(restClientProperties.ai())
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}
