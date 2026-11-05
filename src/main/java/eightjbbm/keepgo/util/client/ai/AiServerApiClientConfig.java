package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({AiServerApiClientProperties.class, AiCallProperties.class})
public class AiServerApiClientConfig {

    private final AiServerApiClientProperties properties;
    private final AiCallProperties callProperties;

    @Bean
    RestClient aiServerRestClient(RestClient.Builder builder) {
        // JDK HttpClient는 자동 재시도를 하지 않는다. 분석 실패는 호출자가 한 번만 시도한다.
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(callProperties.connectTimeout())
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(callProperties.analysisTimeout());

        return builder.requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .baseUrl(properties.address())
                .requestInterceptor((request, body, execution) -> {
                    String safeUri = request.getURI().toString().replaceAll("([?&]key=)[^&]*", "$1***");

                    MediaType contentType = request.getHeaders().getContentType();

                    var charset = contentType != null && contentType.getCharset() != null
                            ? contentType.getCharset()
                            : StandardCharsets.UTF_8;

                    if (log.isInfoEnabled()) {
                        String requestBody = body.length == 0 ? "<empty>" : new String(body, charset);

                        log.info(
                                "AI HTTP request: method={}, uri={}, contentType={}, body={}",
                                request.getMethod(),
                                safeUri,
                                contentType,
                                requestBody);
                    }

                    var response = execution.execute(request, body);

                    String responseBody = StreamUtils.copyToString(response.getBody(), charset);

                    log.info(
                            "RestClient response: method={}, uri={}, status={}, body={}",
                            request.getMethod(),
                            request.getURI(),
                            response.getStatusCode().value(),
                            responseBody);

                    return response;
                })
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
