package eightjbbm.keepgo.integration.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SSE 테스트 클라이언트")
class SseTestClientTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("SSE 이벤트를 도착한 순서대로 수집한다")
    void collectsEventsInOrder() throws Exception {
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeEvent(output, "1", "youtube-sync.accepted", "{\"progress\":0}");
                writeEvent(output, "2", "youtube-sync.progress", "{\"progress\":50}");
                writeEvent(output, "3", "youtube-sync.success", "{\"progress\":100}");
            }
        });

        try (SseTestClient client = SseTestClient.open(uri("/sse"), "token")) {
            assertThat(client.next(Duration.ofSeconds(2)))
                    .isEqualTo(new SseEvent("1", "youtube-sync.accepted", "{\"progress\":0}"));
            assertThat(client.next(Duration.ofSeconds(2)))
                    .isEqualTo(new SseEvent("2", "youtube-sync.progress", "{\"progress\":50}"));
            assertThat(client.next(Duration.ofSeconds(2)))
                    .isEqualTo(new SseEvent("3", "youtube-sync.success", "{\"progress\":100}"));
            client.awaitClosed(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("스트림을 열기 전의 오류는 상태 코드와 본문으로 확인할 수 있다")
    void exposesHandshakeError() {
        server.createContext("/reject", exchange -> {
            byte[] body = "{\"code\":\"YOUTUBE_NOT_CONNECTED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(409, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });

        assertThatThrownBy(() -> SseTestClient.open(uri("/reject"), "token"))
                .isInstanceOfSatisfying(SseOpenException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(409);
                    assertThat(exception.body()).contains("YOUTUBE_NOT_CONNECTED");
                });
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void writeEvent(java.io.OutputStream output, String id, String event, String data)
            throws IOException {
        output.write(("id: " + id + "\nevent: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
