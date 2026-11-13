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
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(callProperties.connectTimeout())
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(callProperties.analysisTimeout());

        return builder.requestFactory(requestFactory)
                .baseUrl(properties.address())
                .requestInterceptor((request, body, execution) -> {
                    MediaType requestType = request.getHeaders().getContentType();
                    java.nio.charset.Charset requestCharset = requestType != null && requestType.getCharset() != null
                            ? requestType.getCharset()
                            : StandardCharsets.UTF_8;
                    var call = new AiHttpLog(
                            request.getMethod().name(), request.getURI(), new String(body, requestCharset));
                    String reason = "io";
                    try {
                        org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
                        try {
                            call.status(response.getStatusCode().value());
                            MediaType responseType = response.getHeaders().getContentType();
                            java.nio.charset.Charset responseCharset =
                                    responseType != null && responseType.getCharset() != null
                                            ? responseType.getCharset()
                                            : StandardCharsets.UTF_8;
                            call.readBody(response.getBody(), responseCharset);
                        } catch (java.io.IOException | RuntimeException exception) {
                            response.close();
                            throw exception;
                        }
                        reason = response.getStatusCode().isError() ? "http_error" : "complete";
                        byte[] responseBytes = call.bodyBytes();
                        return new org.springframework.http.client.ClientHttpResponse() {
                            @Override
                            public org.springframework.http.HttpStatusCode getStatusCode() throws java.io.IOException {
                                return response.getStatusCode();
                            }

                            @Override
                            public String getStatusText() throws java.io.IOException {
                                return response.getStatusText();
                            }

                            @Override
                            public HttpHeaders getHeaders() {
                                return response.getHeaders();
                            }

                            @Override
                            public java.io.InputStream getBody() {
                                return new java.io.ByteArrayInputStream(responseBytes);
                            }

                            @Override
                            public void close() {
                                response.close();
                            }
                        };
                    } catch (java.io.IOException | RuntimeException exception) {
                        reason = exception.getClass().getSimpleName();
                        throw exception;
                    } finally {
                        call.end(reason, true);
                    }
                })
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
