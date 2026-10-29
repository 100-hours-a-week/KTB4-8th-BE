package eightjbbm.keepgo.integration.stub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 서버 HTTP 스텁")
class AiServerStubTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private AiServerStub stub;

    @BeforeEach
    void setUp() {
        stub = new AiServerStub().start();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("받은 요청 수와 최대 동시 요청 수를 기록한다")
    void recordsRequestCountAndMaximumConcurrency() {
        stub.setAnalysisDelay(Duration.ofMillis(300));

        try (var executor = Executors.newFixedThreadPool(3)) {
            List<CompletableFuture<Void>> requests = IntStream.range(0, 3)
                    .mapToObj(index -> CompletableFuture.runAsync(
                            () -> {
                                try {
                                    post("/v1/analyze-video", requestBody("video-" + index));
                                } catch (Exception exception) {
                                    throw new IllegalStateException(exception);
                                }
                            },
                            executor))
                    .toList();
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        }

        assertThat(stub.analysisRequestCount()).isEqualTo(3);
        assertThat(stub.requestCount()).isEqualTo(3);
        assertThat(stub.maxConcurrentRequests()).isGreaterThanOrEqualTo(2);
        assertThat(stub.analysisRequestBodies()).hasSize(3);
    }

    @Test
    @DisplayName("5xx와 잘못된 응답을 주입한다")
    void injectsServerErrorAndMalformedBody() throws Exception {
        stub.failAnalysis(503, "{\"message\":\"down\"}");
        HttpResponse<String> serverError = post("/v1/analyze-video", requestBody("video-a"));
        assertThat(serverError.statusCode()).isEqualTo(503);
        assertThat(serverError.body()).contains("down");

        stub.returnMalformedAnalysis("{\"message\":");
        HttpResponse<String> malformed = post("/v1/analyze-video", requestBody("video-b"));
        assertThat(malformed.statusCode()).isEqualTo(200);
        assertThat(malformed.body()).isEqualTo("{\"message\":");
    }

    @Test
    @DisplayName("임베딩 요청에도 5xx·잘못된 응답·연결 종료를 주입한다")
    void injectsEmbeddingFailures() throws Exception {
        stub.failEmbedding(502, "{\"message\":\"bad\"}");
        assertThat(post("/v1/embed-places", "{}").statusCode()).isEqualTo(502);

        stub.returnMalformedEmbedding("{");
        assertThat(post("/v1/embed-places", "{}").body()).isEqualTo("{");

        stub.resetEmbeddingConnection();
        assertThatThrownBy(() -> post("/v1/embed-places", "{}")).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("지연을 고정해 타임아웃 상황을 만들 수 있다")
    void appliesFixedDelay() throws Exception {
        stub.setAnalysisDelay(Duration.ofMillis(250));

        long startedAt = System.nanoTime();
        post("/v1/analyze-video", requestBody("video-a"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("k6 측정용 통계 조회와 초기화를 제공한다")
    void exposesStatsAndResetForPerfHarness() throws Exception {
        post("/v1/analyze-video", requestBody("video-a"));
        post("/v1/embed-places", "{}");

        HttpResponse<String> stats = get("/__admin/stats");
        assertThat(stats.statusCode()).isEqualTo(200);
        assertThat(stats.body()).contains("\"analysis_requests\":1", "\"embedding_requests\":1");

        assertThat(get("/__admin/reset").statusCode()).isEqualTo(200);
        HttpResponse<String> reset = get("/__admin/stats");
        assertThat(reset.body()).contains("\"analysis_requests\":0", "\"embedding_requests\":0");
    }

    @Test
    @DisplayName("응답 없이 연결을 닫아 전송 실패를 재현한다")
    void closesConnectionWithoutResponse() {
        stub.resetAnalysisConnection();

        assertThatThrownBy(() -> post("/v1/analyze-video", requestBody("video-a")))
                .isInstanceOf(IOException.class);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(stub.baseUrl() + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(stub.baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String requestBody(String videoId) {
        return "{\"video_urls\":[\"https://youtube.com/shorts/" + videoId + "\"]}";
    }
}
