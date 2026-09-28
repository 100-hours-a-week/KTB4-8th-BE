package eightjbbm.keepgo.util.client.google;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GoogleApiClientProperties.class)
public class GoogleApiClientConfig {

    private final GoogleApiClientProperties properties;

    @Bean
    RestClient googleRestClient(
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
