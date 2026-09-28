package eightjbbm.keepgo.util.client.geocoding;

import eightjbbm.keepgo.util.client.ai.AiServerApiClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

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
                .requestInterceptor((request, body, execution) -> {
                    String safeUri = request.getURI()
                            .toString()
                            .replaceAll(
                                    "([?&]key=)[^&]*",
                                    "$1***"
                            );

                    System.out.println(
                            "RestClient request method = "
                                    + request.getMethod()
                    );
                    System.out.println(
                            "RestClient request URI = "
                                    + safeUri
                    );

                    request.getHeaders().forEach((name, values) -> {
                        if (name.equalsIgnoreCase("Authorization")) {
                            System.out.println(name + " = [***]");
                        } else {
                            System.out.println(name + " = " + values);
                        }
                    });

                    if (body.length > 0) {
                        System.out.println(
                                "RestClient request body = "
                                        + new String(
                                        body,
                                        StandardCharsets.UTF_8
                                )
                        );
                    }

                    return execution.execute(request, body);
                })
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}
