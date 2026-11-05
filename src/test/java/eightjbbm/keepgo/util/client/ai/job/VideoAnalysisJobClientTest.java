package eightjbbm.keepgo.util.client.ai.job;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class VideoAnalysisJobClientTest {
    private HttpServer server;
    private VideoAnalysisJobClient client;
    private final AtomicReference<String> request = new AtomicReference<>();
    private boolean delayBody;
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void stop() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    private void serve(int status, String body, String retryAfter, long delayMillis) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze-video", exchange -> {
            calls.incrementAndGet();
            assertEquals("POST", exchange.getRequestMethod());
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (delayBody) {
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                }
                if (delayMillis > 0) Thread.sleep(delayMillis);
                if (!delayBody) exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes, delayBody ? 1 : 0, bytes.length - (delayBody ? 1 : 0));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = new VideoAnalysisJobClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/analyze-video"),
                new ObjectMapper());
    }

    @Test
    void receivesMultipleExplicitGuideTypesAndVideoFailures() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/ai-analysis/mixed-analysis-response.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        serve(200, fixture, null, 0);
        var response = client.analyzeVideo(List.of("video-a", "video-b", "video-c"), Duration.ofMinutes(2));
        assertNotNull(response);
        assertEquals(3, response.results().size());
        assertEquals(
                List.of(VideoAnalysisResponse.GuideType.PLACE, VideoAnalysisResponse.GuideType.EVENT),
                response.results().getFirst().guides().stream()
                        .map(VideoAnalysisResponse.Guide::type)
                        .toList());
        assertNull(response.results().getFirst().guides().get(1).address());
        assertEquals("VIDEO_UNAVAILABLE", response.results().get(2).error().code());
        assertEquals(
                new ObjectMapper()
                        .readTree(
                                "{\"video_urls\":[\"https://youtube.com/shorts/video-a\",\"https://youtube.com/shorts/video-b\",\"https://youtube.com/shorts/video-c\"]}"),
                new ObjectMapper().readTree(request.get()));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-json",
                "null",
                "{}",
                "{\"message\":\"analyze_success\",\"data\":null}",
                "{\"message\":\"analyze_success\",\"data\":{\"results\":[]}}",
                "{\"message\":\"wrong_contract\",\"data\":{\"results\":[{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\",\"error\":null}]}}"
            })
    void rejectsMalformedEnvelopeOrMissingVideos(String body) throws Exception {
        serve(200, body, null, 0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.INVALID_AI_RESPONSE, exception.kind());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"video_url\":\"https://youtube.com/shorts/other\",\"status\":\"not_found\",\"error\":null}",
                "{\"status\":\"not_found\",\"error\":null}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\",\"error\":null}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\",\"error\":null,\"place_name\":\"잘못된 후보\"}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"failed\",\"error\":null}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"failed\",\"error\":{\"code\":\"nested\"}}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\"}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\",\"error\":null},{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\",\"error\":null}",
                "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\",\"error\":null,\"place_type\":\"장소\"},{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"failed\",\"error\":\"timeout\"}"
            })
    void rejectsInvalidIdsStatesAndGuideStructures(String rows) throws Exception {
        serve(200, "{\"message\":\"analyze_success\",\"data\":{\"results\":[" + rows + "]}}", null, 0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.INVALID_AI_RESPONSE, exception.kind());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("알 수 없는 상태는 영상 단위 실패로 보존한다")
    void preservesUnknownStatusForVideoLevelFailure() throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":["
                        + "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"UNKNOWN\",\"error\":null},"
                        + "{\"video_url\":\"https://youtube.com/shorts/b\",\"status\":\"not_found\",\"error\":null}]}}",
                null,
                0);
        var response = client.analyzeVideo(List.of("a", "b"), Duration.ofSeconds(5));

        assertEquals(
                VideoAnalysisResponse.Status.UNKNOWN,
                response.results().getFirst().status());
        assertEquals(
                VideoAnalysisResponse.Status.NOT_FOUND,
                response.results().get(1).status());
    }

    @Test
    @DisplayName("알 수 없는 가이드 유형은 영상 단위 실패로 보존한다")
    void preservesUnknownGuideTypeForVideoLevelFailure() throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":["
                        + "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\","
                        + "\"error\":null,\"place_type\":\"OTHER\"}]}}",
                null,
                0);
        var response = client.analyzeVideo(List.of("a"), Duration.ofSeconds(5));

        assertEquals(
                VideoAnalysisResponse.GuideType.UNKNOWN,
                response.results().getFirst().guides().getFirst().type());
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 401, 422, 429, 500, 502, 503, 504})
    void preservesHttpStatusEvenWhenErrorBodyIsNotJson(int status) throws Exception {
        serve(status, "<html>not-json</html>", "12", 0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(status, exception.statusCode());
        assertEquals("12", exception.retryAfter());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Wed, 28 Oct 2026 12:00:00 GMT", "invalid-value"})
    void preservesRawRetryAfterForJobRetryPolicy(String header) throws Exception {
        serve(429, "bad-body", header, 0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(header, exception.retryAfter());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(5)
    void limitsHeadersAndEntireBodyByRemainingJobTime(boolean slowBody) throws Exception {
        delayBody = slowBody;
        serve(200, "{}", null, 1200);
        long started = System.nanoTime();
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofMillis(300)));
        assertEquals(AiRequestException.Kind.TIMEOUT, exception.kind());
        assertNotNull(exception.getCause());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
        assertTrue(calls.get() <= 1);
    }

    @Test
    void doesNotCallServerAfterJobDeadline() throws Exception {
        serve(200, "{}", null, 0);
        var exception = assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ZERO));
        assertEquals(AiRequestException.Kind.TIMEOUT, exception.kind());
        assertEquals(0, calls.get());
    }

    @Test
    void preservesTransportFailureCause() throws Exception {
        serve(200, "{}", null, 0);
        server.stop(0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.TRANSPORT_ERROR, exception.kind());
        assertInstanceOf(java.net.ConnectException.class, exception.getCause());
    }

    @Test
    void preservesCallerInterrupt() throws Exception {
        serve(200, "{}", null, 1200);
        try {
            Thread.currentThread().interrupt();
            var exception = assertThrows(
                    AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
            assertEquals(AiRequestException.Kind.INTERRUPTED, exception.kind());
            assertInstanceOf(InterruptedException.class, exception.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AI_ANALYSIS_CONTRACT_URL", matches = "https?://.+")
    void verifiesActualServerContractWhenExplicitlyConfigured() {
        var ids = List.of(System.getenv("AI_ANALYSIS_CONTRACT_VIDEO_IDS").split(","));
        client = new VideoAnalysisJobClient(URI.create(System.getenv("AI_ANALYSIS_CONTRACT_URL")), new ObjectMapper());
        var response = client.analyzeVideo(ids, Duration.ofSeconds(130));
        assertEquals(
                new java.util.HashSet<>(ids),
                response.results().stream()
                        .map(VideoAnalysisResponse.Result::videoId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void decodesSpreadsheetExampleIncludingBusinessHoursAndRepeatedVideoUrl() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/ai-analysis/spreadsheet-analysis-response.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        serve(200, fixture, null, 0);
        var response = client.analyzeVideo(List.of("aaaa", "bbbb", "cccc", "dddd"), Duration.ofSeconds(5));
        assertEquals(4, response.results().size());
        assertEquals(2, response.results().get(1).guides().size());
        var place = response.results().getFirst().guides().getFirst();
        assertEquals("메가커피 성수점", place.name());
        assertEquals(0.95, place.confidence());
        assertEquals(
                java.time.LocalTime.of(8, 0),
                place.businessHours()
                        .weeklyHours()
                        .get(java.time.DayOfWeek.MONDAY)
                        .getFirst()
                        .openTime());
        assertTrue(place.businessHours()
                .exceptions()
                .get(java.time.LocalDate.of(2026, 10, 20))
                .isEmpty());
        assertEquals("llm_timeout", response.results().get(3).error().code());
        assertEquals(
                VideoAnalysisResponse.GuideType.EVENT,
                response.results().get(1).guides().get(1).type());
    }

    @Test
    void preservesHttpFailureBeforeReadingDelayedErrorBody() throws Exception {
        delayBody = true;
        serve(503, "unavailable", "12", 1200);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofMillis(300)));
        assertEquals(AiRequestException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(503, exception.statusCode());
        assertEquals("12", exception.retryAfter());
    }

    @ParameterizedTest
    @MethodSource("invalidDates")
    void rejectsInvalidDateWireValues(String field, String value) throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":[{"
                        + "\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\","
                        + "\"error\":null,\"place_type\":\"이벤트\",\"" + field + "\":" + value + "}]}}",
                null,
                0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.INVALID_AI_RESPONSE, exception.kind());
    }

    static java.util.stream.Stream<Arguments> invalidDates() {
        return java.util.stream.Stream.of("event_start_date", "event_end_date")
                .flatMap(field -> java.util.stream.Stream.of("[2026,10,28]", "\"2026-10-28T12:34:56\"", "\"\"")
                        .map(value -> Arguments.of(field, value)));
    }

    @Test
    void neverRetriesImmediatelyRetryableHttpFailure() throws Exception {
        serve(503, "unavailable", "0", 0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"place_name\":42",
                "\"address\":true",
                "\"summary\":4.5",
                "\"business_hours\":{\"timezone\":42}"
            })
    void rejectsNumbersAndBooleansInTextFields(String candidateFields) throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":[{"
                        + "\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\","
                        + "\"error\":null,\"place_type\":\"장소\"," + candidateFields + "}]}}",
                null,
                0);
        var exception =
                assertThrows(AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Duration.ofSeconds(5)));
        assertEquals(AiRequestException.Kind.INVALID_AI_RESPONSE, exception.kind());
    }
}
