package eightjbbm.keepgo.integration.stub;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 테스트와 성능 측정에서 함께 쓰는 AI 서버 HTTP 스텁.
///
/// 실제 HTTP 서버이므로 `AI_SERVER_API_ADDRESS`만 이 주소로 바꾸면 애플리케이션 코드를
/// 수정하지 않고 분석·임베딩 요청을 가로챌 수 있다. 지연, 5xx, 잘못된 본문, 연결 종료를
/// 테스트마다 주입하고 받은 요청 수와 최대 동시 요청 수를 관찰한다.
public final class AiServerStub implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AiServerStub.class);
    private static final String ANALYZE_PATH = "/v1/analyze-video";
    private static final String EMBED_PATH = "/v1/embed-places";
    private static final Pattern VIDEO_URL = Pattern.compile("https://youtube\\.com/shorts/[A-Za-z0-9_-]+");
    private static final Pattern VIDEO_ID = Pattern.compile("https://youtube\\.com/shorts/([A-Za-z0-9_-]+)");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicInteger analysisRequests = new AtomicInteger();
    private final AtomicInteger embeddingRequests = new AtomicInteger();
    private final List<String> analysisRequestBodies = new CopyOnWriteArrayList<>();
    private final List<String> embeddingRequestBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<Duration> analysisDelay = new AtomicReference<>(Duration.ZERO);
    private final AtomicReference<Duration> embeddingDelay = new AtomicReference<>(Duration.ZERO);
    private final AtomicReference<StubResponse> analysisResponse = new AtomicReference<>();
    private final AtomicReference<StubResponse> embeddingResponse = new AtomicReference<>();
    private volatile HttpServer server;

    public synchronized AiServerStub start() {
        return start(0);
    }

    public synchronized AiServerStub start(int port) {
        if (server != null) {
            return this;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("AI 스텁 서버를 시작하지 못했습니다", exception);
        }
        server.createContext(ANALYZE_PATH, exchange -> handle(exchange, true));
        server.createContext(EMBED_PATH, exchange -> handle(exchange, false));
        server.createContext("/__admin/stats", exchange -> write(exchange, new StubResponse(200, statsJson(), false)));
        server.createContext("/__admin/reset", exchange -> {
            resetStats();
            write(exchange, new StubResponse(200, "{\"message\":\"reset\"}", false));
        });
        server.setExecutor(executor);
        server.start();
        return this;
    }

    public String baseUrl() {
        HttpServer running = server;
        if (running == null) {
            throw new IllegalStateException("AI 스텁 서버가 아직 시작되지 않았습니다");
        }
        return "http://127.0.0.1:" + running.getAddress().getPort();
    }

    /// k6 측정 사이에 요청 수만 초기화한다. 지연·오류 주입 설정은 유지한다.
    public void resetStats() {
        analysisRequests.set(0);
        embeddingRequests.set(0);
        analysisRequestBodies.clear();
        embeddingRequestBodies.clear();
        maxInFlight.set(0);
    }

    public void reset() {
        resetStats();
        analysisDelay.set(Duration.ZERO);
        embeddingDelay.set(Duration.ZERO);
        analysisResponse.set(null);
        embeddingResponse.set(null);
    }

    public void setAnalysisDelay(Duration delay) {
        analysisDelay.set(delay);
    }

    public void setEmbeddingDelay(Duration delay) {
        embeddingDelay.set(delay);
    }

    public void failAnalysis(int status, String body) {
        analysisResponse.set(new StubResponse(status, body, false));
    }

    public void failEmbedding(int status, String body) {
        embeddingResponse.set(new StubResponse(status, body, false));
    }

    /// 잘못된 JSON처럼 계약을 위반하는 200 응답을 주입한다.
    public void returnMalformedAnalysis(String body) {
        analysisResponse.set(new StubResponse(200, body, false));
    }

    /// 잘못된 JSON처럼 계약을 위반하는 임베딩 응답을 주입한다.
    public void returnMalformedEmbedding(String body) {
        embeddingResponse.set(new StubResponse(200, body, false));
    }

    /// 응답을 쓰지 않고 연결을 닫아 분석 요청의 전송 실패를 재현한다.
    public void resetAnalysisConnection() {
        analysisResponse.set(new StubResponse(0, null, true));
    }

    /// 응답을 쓰지 않고 연결을 닫아 임베딩 요청의 전송 실패를 재현한다.
    public void resetEmbeddingConnection() {
        embeddingResponse.set(new StubResponse(0, null, true));
    }

    public int requestCount() {
        return analysisRequests.get() + embeddingRequests.get();
    }

    public int analysisRequestCount() {
        return analysisRequests.get();
    }

    public int embeddingRequestCount() {
        return embeddingRequests.get();
    }

    public int maxConcurrentRequests() {
        return maxInFlight.get();
    }

    public List<String> analysisRequestBodies() {
        return List.copyOf(analysisRequestBodies);
    }

    public List<String> embeddingRequestBodies() {
        return List.copyOf(embeddingRequestBodies);
    }

    private String statsJson() {
        return """
                {"analysis_requests":%d,"embedding_requests":%d,"request_count":%d,"max_concurrent_requests":%d}
                """.formatted(analysisRequests.get(), embeddingRequests.get(), requestCount(), maxInFlight.get());
    }

    private void handle(HttpExchange exchange, boolean analysis) throws IOException {
        int active = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(active, Math::max);
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (analysis) {
                analysisRequests.incrementAndGet();
                analysisRequestBodies.add(requestBody);
            } else {
                embeddingRequests.incrementAndGet();
                embeddingRequestBodies.add(requestBody);
            }

            sleep(analysis ? analysisDelay.get() : embeddingDelay.get());

            StubResponse response = analysis ? analysisResponse.get() : embeddingResponse.get();
            if (response != null && response.closeConnection()) {
                exchange.close();
                return;
            }

            if (response == null) {
                response = analysis
                        ? new StubResponse(200, successfulAnalysis(requestBody), false)
                        : new StubResponse(200, "{\"message\":\"ok\"}", false);
            }

            write(exchange, response);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static void write(HttpExchange exchange, StubResponse response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String successfulAnalysis(String requestBody) {
        List<String> urls = VIDEO_URL
                .matcher(requestBody)
                .results()
                .map(result -> result.group())
                .toList();
        List<String> results = urls.stream()
                .map(url -> {
                    Matcher idMatcher = VIDEO_ID.matcher(url);
                    String id = idMatcher.find() ? idMatcher.group(1) : url;
                    return """
                            {"video_url":"%s","status":"success","error":null,"place_name":"스텁 가이드 %s","address":"서울특별시 종로구 스텁로 %s","location_detail":"스텁 상세","category":"장소","business_hours":null,"event_start_date":null,"event_end_date":null,"event_period_unresolved":false,"lat":37.5,"lng":127.0,"confidence":0.9,"summary":"스텁 설명"}
                            """.formatted(url, id, id);
                })
                .toList();
        return "{\"message\":\"ok\",\"data\":{\"results\":[" + String.join(",", results) + "]}}";
    }

    private static void sleep(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        HttpServer running = server;
        server = null;
        if (running != null) {
            running.stop(0);
        }
        executor.shutdownNow();
    }

    /// k6 같은 외부 프로세스에서도 같은 스텁을 띄울 수 있게 한다.
    public static void main(String[] args) throws InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("AI_STUB_PORT", "18080"));
        long delayMillis = Long.parseLong(System.getenv().getOrDefault("AI_STUB_DELAY_MS", "0"));

        AiServerStub stub = new AiServerStub().start(port);
        stub.setAnalysisDelay(Duration.ofMillis(delayMillis));
        stub.setEmbeddingDelay(Duration.ofMillis(delayMillis));
        log.info("AI stub listening on {}", stub.baseUrl());
        Runtime.getRuntime().addShutdownHook(new Thread(stub::close));
        Thread.currentThread().join();
    }

    private record StubResponse(int status, String body, boolean closeConnection) {}
}
