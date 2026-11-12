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

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@DisplayName("AI 서버 예열·버전 정보 클라이언트")
class AiServerApiClientTest {
    private HttpServer server;
    private boolean slowBody;
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    private final AtomicReference<String> requestLine = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private AiServerApiClient serve(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            requestLine.set(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            if (slowBody) {
                exchange.getResponseBody().write(bytes, 0, 1);
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                exchange.getResponseBody().write(bytes, slowBody ? 1 : 0, bytes.length - (slowBody ? 1 : 0));
            } finally {
                exchange.close();
            }
        });
        server.start();
        return new AiServerApiClient(
                configuredClient("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private RestClient configuredClient(String address) {
        return configuredClient(address, java.time.Duration.ofSeconds(130));
    }

    private RestClient configuredClient(String address, java.time.Duration timeout) {
        var props = new eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties(
                3,
                1,
                java.time.Duration.ofSeconds(150),
                java.time.Duration.ofMillis(200),
                timeout,
                timeout.compareTo(java.time.Duration.ofSeconds(5)) < 0 ? timeout : java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(30),
                timeout.dividedBy(2));
        return new AiServerApiClientConfig(new AiServerApiClientProperties(address), props)
                .aiServerRestClient(RestClient.builder()
                        .defaultHeader("Authorization", "Bearer HEADER_SECRET")
                        .defaultHeader("X-API-Key", "KEY_SECRET"));
    }

    @Test
    @DisplayName("실제 RestClient의 지연 오류 본문도 제한 안에 중단하고 수신 상태를 남긴다")
    void logsPartialBodyOnTimeout(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        slowBody = true;
        serve(503, "{\"message\":\"late\"}");
        var rest =
                configuredClient("http://127.0.0.1:" + server.getAddress().getPort(), java.time.Duration.ofMillis(300));
        long start = System.nanoTime();
        assertThatThrownBy(() ->
                        rest.get().uri("/v3/version?key=URI_SECRET").retrieve().body(String.class))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(1));
        assertThat(output.getOut())
                .contains("status=503", "complete=false, body={", "reason=")
                .doesNotContain("URI_SECRET");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 설정에서 임베딩 요청과 전체 응답을 남기고 반환 동작을 보존한다")
    void logsEmbedding(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        serve(200, "{\"success\":true,\"place_id\":7}");
        var rest = configuredClient("http://127.0.0.1:" + server.getAddress().getPort());
        var guide = new eightjbbm.keepgo.recommendation.entity.OutingPlace(
                "카페", "성수 카페", "조용한 장소", "상세", "서울특별시 성동구", new eightjbbm.keepgo.util.Coordinate(37.5f, 127.0f), null);
        org.springframework.test.util.ReflectionTestUtils.setField(guide, "id", 7L);
        var service = new eightjbbm.keepgo.util.client.ai.analysis.AiServerService(rest);
        service.embedPlace(guide);
        assertThat(requestLine.get()).isEqualTo("POST /v3/embed-places");
        assertThat(output.getOut())
                .contains(
                        "path=/v3/embed-places",
                        "성수 카페",
                        "조용한 장소",
                        "body={\"success\":true,\"place_id\":7}",
                        "reason=complete");
    }

    @Test
    @DisplayName("동시 호출의 요청·응답·종료는 서로 다른 ID로 연결하고 인증값을 제외한다")
    void logsConcurrentCalls(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        serve(200, "{\"message\":\"warmup_accepted\",\"data\":null}");
        var client = new AiServerApiClient(configuredClient("http://USER_SECRET:PASS_SECRET@127.0.0.1:"
                + server.getAddress().getPort()));
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(client::warmUp);
            var two = executor.submit(client::warmUp);
            assertThat(one.get().message()).isEqualTo("warmup_accepted");
            assertThat(two.get().message()).isEqualTo("warmup_accepted");
        }
        var lines =
                output.getOut().lines().filter(line -> line.contains("AI HTTP")).toList();
        assertThat(lines).hasSize(8);
        var groups = lines.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        line -> line.split("callId=")[1].split(",")[0]));
        assertThat(groups).hasSize(2);
        assertThat(groups.values()).allSatisfy(call -> {
            assertThat(call).hasSize(4);
            assertThat(call).anySatisfy(line -> assertThat(line).contains("AI HTTP request"));
            assertThat(call)
                    .anySatisfy(line -> assertThat(line).contains("AI HTTP body", "warmup_accepted", "complete=true"));
            assertThat(call)
                    .anySatisfy(line ->
                            assertThat(line).contains("AI HTTP end", "status=200", "reason=complete", "elapsedMs="));
        });
        assertThat(output.getOut()).doesNotContain("HEADER_SECRET", "KEY_SECRET", "USER_SECRET", "PASS_SECRET");
    }

    @Test
    @DisplayName("긴 HTTP 오류 본문을 생략하지 않고 URI query 인증값을 제외한다")
    void logsErrorWithSafeUri(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String body = "한글\n".repeat(3000) + "끝\r\t";
        serve(503, body);
        var rest = configuredClient("http://127.0.0.1:" + server.getAddress().getPort());
        assertThatThrownBy(() -> rest.get()
                        .uri("/v3/version?api_key=QUERY_SECRET&token=TOKEN_SECRET")
                        .retrieve()
                        .body(String.class))
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception ->
                                assertThat(exception.getResponseBodyAsString()).isEqualTo(body));
        assertThat(output.getOut())
                .contains("한글\\n".repeat(3000), "끝\\r\\t", "reason=http_error", "status=503", "complete=true")
                .doesNotContain("QUERY_SECRET", "TOKEN_SECRET");
    }

    @Test
    @DisplayName("예열은 POST /v3/warmup을 호출하고 warmup_accepted 응답을 해석한다")
    void warmUpCallsPathAndReadsMessage() throws Exception {
        var client = serve(200, "{\"message\":\"warmup_accepted\",\"data\":null}");

        var response = client.warmUp();

        assertThat(requestLine.get()).isEqualTo("POST /v3/warmup");
        assertThat(response.message()).isEqualTo("warmup_accepted");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("버전 정보는 GET /v3/version을 호출하고 환경과 commit을 해석한다")
    void getVersionCallsPathAndReadsEnvironmentAndCommit() throws Exception {
        var client = serve(
                200, "{\"message\":\"version_success\",\"data\":{\"environment\":\"dev\",\"commit\":\"d5917fc\"}}");

        var response = client.getVersion();

        assertThat(requestLine.get()).isEqualTo("GET /v3/version");
        assertThat(response.message()).isEqualTo("version_success");
        assertThat(response.data().environment()).isEqualTo("dev");
        assertThat(response.data().commit()).isEqualTo("d5917fc");
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    @DisplayName("AI 서버가 오류 상태를 응답하면 예열 호출은 예외를 던진다")
    void warmUpThrowsOnErrorStatus() throws Exception {
        var client = serve(502, "{\"message\":\"llm_invalid_response\",\"data\":null}");

        assertThatThrownBy(client::warmUp).isInstanceOf(RestClientResponseException.class);
    }
}
