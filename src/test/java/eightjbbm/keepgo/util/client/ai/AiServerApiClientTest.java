package eightjbbm.keepgo.util.client.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@DisplayName("AI 서버 예열·버전 정보 클라이언트")
class AiServerApiClientTest {
    private HttpServer server;
    private final AtomicReference<String> requestLine = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private AiServerApiClient serve(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestLine.set(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new AiServerApiClient(RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build());
    }

    @Test
    @DisplayName("예열은 POST /v3/warmip을 호출하고 warmup_accepted 응답을 해석한다")
    void warmUpCallsPathAndReadsMessage() throws Exception {
        var client = serve(200, "{\"message\":\"warmup_accepted\",\"data\":null}");

        var response = client.warmUp();

        assertThat(requestLine.get()).isEqualTo("POST /v3/warmip");
        assertThat(response.message()).isEqualTo("warmup_accepted");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("버전 정보는 GET /v3/version을 호출하고 환경과 commit을 해석한다")
    void versionCallsPathAndReadsEnvironmentAndCommit() throws Exception {
        var client = serve(
                200, "{\"message\":\"version_success\",\"data\":{\"environment\":\"dev\",\"commit\":\"d5917fc\"}}");

        var response = client.version();

        assertThat(requestLine.get()).isEqualTo("GET /v3/version");
        assertThat(response.message()).isEqualTo("version_success");
        assertThat(response.data().environment()).isEqualTo("dev");
        assertThat(response.data().commit()).isEqualTo("d5917fc");
    }

    @Test
    @DisplayName("AI 서버가 오류 상태를 응답하면 예열 호출은 예외를 던진다")
    void warmUpThrowsOnErrorStatus() throws Exception {
        var client = serve(502, "{\"message\":\"llm_invalid_response\",\"data\":null}");

        assertThatThrownBy(client::warmUp).isInstanceOf(RestClientResponseException.class);
    }
}
