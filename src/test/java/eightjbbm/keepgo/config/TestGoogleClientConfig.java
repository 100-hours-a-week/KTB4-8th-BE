package eightjbbm.keepgo.config;

import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class TestGoogleClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    private MockRestServiceServer mockServer;

    @Bean
    RestClient googleRestClient(
            RestClient.Builder builder
    ) {
        var b = builder
                .baseUrl("https://www.googleapis.com")
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                );

        mockServer = MockRestServiceServer.bindTo(b).build();

        return b.build();
    }
}
