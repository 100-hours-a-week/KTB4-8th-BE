package eightjbbm.keepgo.util.client.ai.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AI SSE 수신 규칙")
class AiSseClientTest {
    private static final String DELTA_1 = "event: delta\ndata: {\"text\":\"성수에서 \"}\n\n";
    private static final String DELTA_2 = "event: delta\ndata: {\"text\":\"카페\"}\n\n";
    private static final String DONE = "event: done\ndata: {}\n\n";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestHead = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private ServerSocket server;
    private AiSseClient client;

    @AfterEach
    void stop() throws Exception {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    /// 스크립트가 끝나면 소켓을 닫는다. 마지막 청크를 쓰지 않으면 본문 도중에 연결이 끊긴 것과 같다.
    private void serve(Script script) throws Exception {
        serve(script, Duration.ofSeconds(10));
    }

    private void serve(Script script, Duration totalTimeout) throws Exception {
        server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(() -> {
            try (Socket socket = server.accept()) {
                readRequest(socket);
                script.run(socket, socket.getOutputStream());
            } catch (Exception ignored) {
                // 테스트가 먼저 끝나 서버를 닫으면 발생한다.
            }
        });
        client = new AiSseClient(
                URI.create("http://127.0.0.1:" + server.getLocalPort()),
                mapper,
                Duration.ofSeconds(2),
                Duration.ofMillis(400),
                totalTimeout);
    }

    private void readRequest(Socket socket) throws Exception {
        var in = socket.getInputStream();
        var head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.UTF_8).endsWith("\r\n\r\n")) head.write(in.read());
        requestHead.set(head.toString(StandardCharsets.UTF_8));
        int length = requestHead
                .get()
                .lines()
                .filter(line -> line.toLowerCase().startsWith("content-length:"))
                .mapToInt(line -> Integer.parseInt(line.substring(15).trim()))
                .findFirst()
                .orElse(0);
        requestBody.set(new String(in.readNBytes(length), StandardCharsets.UTF_8));
    }

    private static void streamHead(OutputStream out) throws Exception {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void chunk(OutputStream out, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void endChunks(OutputStream out) throws Exception {
        out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void httpError(OutputStream out, int status) throws Exception {
        httpResponse(out, status, "Content-Type: application/json\r\n", "{\"message\":\"error\",\"data\":null}");
    }

    private static void httpResponse(OutputStream out, int status, String headers, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + " X\r\n" + headers + "Content-Length: " + bytes.length + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private AiStreamException failure(List<AiStreamEvent> seen) {
        return catchThrowableOfType(
                AiStreamException.class, () -> client.stream("/v3/extract/stream", Map.of("chat", "안녕"), seen::add));
    }

    @Test
    @DisplayName("delta를 순서대로 이어 붙이고 done을 받으면 종료한다")
    void concatenatesDeltaAndEndsOnDone() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
            chunk(out, DELTA_2);
            chunk(out, "event: done\ndata: {\"slot\":{\"category\":[\"카페\"]},\"query\":\"조용한\"}\n\n");
            endChunks(out);
        });
        var seen = new ArrayList<AiStreamEvent>();

        var result = client.stream("/v3/extract/stream", Map.of("chat", "안녕"), seen::add);

        assertThat(result.text()).isEqualTo("성수에서 카페");
        assertThat(result.done().path("query").asString()).isEqualTo("조용한");
        assertThat(result.done().path("slot").path("category").get(0).asString())
                .isEqualTo("카페");
        assertThat(seen).extracting(AiStreamEvent::name).containsExactly("delta", "delta");
        assertThat(requestHead.get())
                .startsWith("POST /v3/extract/stream HTTP/1.1")
                .containsIgnoringCase("text/event-stream");
        assertThat(mapper.readTree(requestBody.get()).path("chat").asString()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("여러 data 줄로 나뉜 이벤트와 keep-alive 주석을 읽고 status를 전달한다")
    void readsNamedEventsAndStatus() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, "event: status\ndata: {\"stage\":\"retrieving\"}\n\n");
            chunk(out, ": keep-alive\n\n");
            chunk(out, "event: done\ndata: {\"message\":\"recommend_success\",\n");
            chunk(out, "data: \"courses\":[]}\n\n");
            endChunks(out);
        });
        var seen = new ArrayList<AiStreamEvent>();

        var result = client.stream("v3/recommend-course/stream", Map.of(), seen::add);

        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().data().path("stage").asString()).isEqualTo("retrieving");
        assertThat(result.done().path("message").asString()).isEqualTo("recommend_success");
        assertThat(requestHead.get()).startsWith("POST /v3/recommend-course/stream HTTP/1.1");
    }

    @ParameterizedTest(name = "error 이벤트 {0}")
    @DisplayName("스트림 중 error 이벤트는 이후 이벤트를 읽지 않고 BE 오류로 변환한다")
    @CsvSource({
        "llm_timeout,eightjbbm.keepgo.util.client.ai.AiLlmTimeoutException,502",
        "llm_rate_limited,eightjbbm.keepgo.util.client.ai.AiLlmRateLimitedException,502",
        "llm_invalid_response,eightjbbm.keepgo.util.client.ai.AiLlmInvalidResponseException,502",
        "internal_server_error,eightjbbm.keepgo.util.exception.ServiceUnavailableException,503",
        "unknown_message,eightjbbm.keepgo.util.exception.ServiceUnavailableException,503"
    })
    void convertsErrorEvent(String message, Class<? extends ErrorResponseException> type, int status) throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
            chunk(out, "event: error\ndata: {\"message\":\"" + message + "\"}\n\n");
            chunk(out, DONE);
            endChunks(out);
        });
        var seen = new ArrayList<AiStreamEvent>();

        var thrown = failure(seen);

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(type);
        assertThat(thrown.error().getStatusCode().value()).isEqualTo(status);
        assertThat(seen).hasSize(1);
    }

    @Test
    @DisplayName("done·error 없이 소켓이 정상 종료되면 실패하고 받은 delta는 결과로 남지 않는다")
    void failsWhenSocketClosesWithoutTerminalEvent() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
        });
        var seen = new ArrayList<AiStreamEvent>();

        var thrown = failure(seen);

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
        assertThat(seen).hasSize(1);
    }

    @Test
    @DisplayName("done·error 없이 응답 본문이 정상적으로 끝나도 실패한다")
    void failsWhenBodyEndsCleanlyWithoutTerminalEvent() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
            endChunks(out);
        });

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("이름이 없거나 알 수 없는 이벤트는 JSON이 아니어도 무시한다")
    void ignoresUnknownEventWithoutParsing() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, "event: ping\ndata: keepalive\n\n");
            chunk(out, "data: {\"event\":\"error\",\"data\":{\"message\":\"llm_timeout\"}}\n\n");
            chunk(out, DONE);
            endChunks(out);
        });

        var result = client.stream("/v3/extract/stream", Map.of(), event -> {});

        assertThat(result.text()).isEmpty();
    }

    @ParameterizedTest(name = "잘못된 이벤트 #{index}")
    @DisplayName("해석할 수 없는 이벤트는 스트림 도중 실패로 변환한다")
    @ValueSource(
            strings = {
                "event: delta\ndata: {broken\n\n",
                "event: delta\ndata: {}\n\n",
                "event: delta\ndata: {\"text\":1}\n\n"
            })
    void failsOnMalformedEvent(String event) throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, event);
            chunk(out, DONE);
            endChunks(out);
        });

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("error 이벤트의 message가 문자열이 아니어도 503으로 변환한다")
    void convertsErrorEventWithNonStringMessage() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, "event: error\ndata: {\"message\":{\"code\":1}}\n\n");
            endChunks(out);
        });

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("done 이벤트가 완성되기 전에 소켓이 재설정되어도 실패한다")
    void failsWhenSocketResetsBeforeDoneCompletes() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, "event: done\ndata: {\"query\":\"조용한\"}\n");
            socket.setSoLinger(true, 0);
        });

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("이벤트 사이 지연이 제한을 넘으면 실패한다")
    void failsWhenStreamStalls() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
            Thread.sleep(3000);
        });

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("이벤트가 제때 와도 전체 상한을 넘으면 실패한다")
    void failsWhenStreamExceedsTotalTimeout() throws Exception {
        serve(
                (socket, out) -> {
                    streamHead(out);
                    for (int i = 0; i < 30; i++) {
                        chunk(out, DELTA_1);
                        Thread.sleep(100);
                    }
                    chunk(out, DONE);
                    endChunks(out);
                },
                Duration.ofMillis(600));
        var seen = new ArrayList<AiStreamEvent>();
        long start = System.nanoTime();

        var thrown = failure(seen);

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.IN_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(seen).isNotEmpty().hasSizeLessThan(30);
    }

    @Test
    @DisplayName("지연이 제한 안이면 느린 스트림도 완료한다")
    void completesSlowStreamWithinLimit() throws Exception {
        serve((socket, out) -> {
            streamHead(out);
            chunk(out, DELTA_1);
            Thread.sleep(100);
            chunk(out, DELTA_2);
            Thread.sleep(100);
            chunk(out, DONE);
            endChunks(out);
        });

        var result = client.stream("/v3/extract/stream", Map.of(), event -> {});

        assertThat(result.text()).isEqualTo("성수에서 카페");
    }

    @ParameterizedTest(name = "HTTP {0}")
    @DisplayName("스트림 시작 전 HTTP 오류는 이벤트를 읽지 않고 BE 오류로 변환한다")
    @CsvSource({
        "400,eightjbbm.keepgo.util.exception.ServiceUnavailableException,503",
        "422,eightjbbm.keepgo.util.exception.ServiceUnavailableException,503",
        "429,eightjbbm.keepgo.util.client.ai.AiLlmRateLimitedException,502",
        "500,eightjbbm.keepgo.util.exception.ServiceUnavailableException,503",
        "502,eightjbbm.keepgo.util.client.ai.AiLlmInvalidResponseException,502",
        "504,eightjbbm.keepgo.util.client.ai.AiLlmTimeoutException,502"
    })
    void convertsHttpErrorBeforeStream(int httpStatus, Class<? extends ErrorResponseException> type, int status)
            throws Exception {
        serve((socket, out) -> httpError(out, httpStatus));
        var seen = new ArrayList<AiStreamEvent>();

        var thrown = failure(seen);

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.BEFORE_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(type);
        assertThat(thrown.error().getStatusCode().value()).isEqualTo(status);
        assertThat(seen).isEmpty();
    }

    @ParameterizedTest(name = "HTTP {0}, 본문 {1}")
    @DisplayName("HTTP 오류 본문의 llm 사유를 상태보다 우선하고, 본문이 없거나 JSON이 아니면 상태로 분류한다")
    @CsvSource(
            delimiter = '|',
            value = {
                "502|{\"message\":\"llm_timeout\",\"data\":null}|eightjbbm.keepgo.util.client.ai.AiLlmTimeoutException",
                "500|{\"message\":\"llm_rate_limited\",\"data\":null}|eightjbbm.keepgo.util.client.ai.AiLlmRateLimitedException",
                "502||eightjbbm.keepgo.util.client.ai.AiLlmInvalidResponseException",
                "502|<html>Bad Gateway</html>|eightjbbm.keepgo.util.client.ai.AiLlmInvalidResponseException",
                "503|<html>Unavailable</html>|eightjbbm.keepgo.util.exception.ServiceUnavailableException"
            })
    void prefersLlmReasonInHttpErrorBody(int httpStatus, String body, Class<? extends ErrorResponseException> type)
            throws Exception {
        serve((socket, out) -> httpResponse(out, httpStatus, "", body == null ? "" : body));

        var thrown = failure(new ArrayList<>());

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.BEFORE_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(type);
    }

    @ParameterizedTest(name = "Content-Type 헤더 #{index}")
    @DisplayName("200이어도 text/event-stream이 아니면 스트림 시작 전 실패로 변환한다")
    @ValueSource(strings = {"Content-Type: application/json\r\n", "Content-Type: text/event-streaming\r\n", ""})
    void failsBeforeStreamWhenContentTypeIsNotEventStream(String headers) throws Exception {
        serve((socket, out) -> httpResponse(out, 200, headers, "{\"message\":\"extract_success\"}"));
        var seen = new ArrayList<AiStreamEvent>();

        var thrown = failure(seen);

        assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.BEFORE_STREAM);
        assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
        assertThat(seen).isEmpty();
    }

    @Test
    @DisplayName("text/event-stream에 charset 같은 파라미터가 붙어도 스트림으로 읽는다")
    void acceptsEventStreamWithParameters() throws Exception {
        serve((socket, out) -> {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: Text/Event-Stream; charset=utf-8\r\n"
                            + "Transfer-Encoding: chunked\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            chunk(out, DELTA_1);
            chunk(out, DONE);
            endChunks(out);
        });

        var result = client.stream("/v3/extract/stream", Map.of(), event -> {});

        assertThat(result.text()).isEqualTo("성수에서 ");
    }

    @Test
    @DisplayName("연결할 수 없으면 스트림 시작 전 실패로 변환한다")
    void failsBeforeStreamWhenConnectionRefused() throws Exception {
        int unusedPort;
        try (var socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        client = new AiSseClient(
                URI.create("http://127.0.0.1:" + unusedPort),
                mapper,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> client.stream("/v3/extract/stream", Map.of(), event -> {}))
                .isInstanceOfSatisfying(AiStreamException.class, thrown -> {
                    assertThat(thrown.phase()).isEqualTo(AiStreamException.Phase.BEFORE_STREAM);
                    assertThat(thrown.error()).isExactlyInstanceOf(ServiceUnavailableException.class);
                });
    }

    @FunctionalInterface
    private interface Script {
        void run(Socket socket, OutputStream out) throws Exception;
    }
}
