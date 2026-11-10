package eightjbbm.keepgo.util.client.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

@DisplayName("영상 분석 Job 클라이언트")
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
        server.createContext("/v3/analyze-video", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
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
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/analyze-video"),
                new ObjectMapper());
    }

    @Test
    @DisplayName("복수 가이드 유형과 영상별 실패를 받고, 요청 본문의 업로드 날짜는 yyyy-MM-dd 문자열이다")
    void receivesMultipleExplicitGuideTypesAndVideoFailures() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/ai-analysis/mixed-analysis-response.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        serve(200, fixture, null, 0);
        var response = client.analyzeVideo(
                List.of("video-a", "video-b", "video-c"),
                Map.of("video-a", new VideoMeta(LocalDate.of(2026, 11, 2), "제목", "설명")),
                Duration.ofMinutes(2));
        assertThat(response).isNotNull();
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().getFirst().guides().stream()
                        .map(VideoAnalysisResponse.Guide::type)
                        .toList())
                .isEqualTo(List.of(VideoAnalysisResponse.GuideType.PLACE, VideoAnalysisResponse.GuideType.EVENT));
        assertThat(response.results().getFirst().guides().get(1).address()).isNull();
        assertThat(response.results().get(2).error().code()).isEqualTo("VIDEO_UNAVAILABLE");
        assertThat(new ObjectMapper().readTree(request.get())).isEqualTo(new ObjectMapper().readTree("""
                                {"videos":[
                                  {"video_url":"https://youtube.com/shorts/video-a","upload_date":"2026-11-02","title":"제목","description":"설명"},
                                  {"video_url":"https://youtube.com/shorts/video-b","upload_date":null,"title":null,"description":null},
                                  {"video_url":"https://youtube.com/shorts/video-c","upload_date":null,"title":null,"description":null}]}
                                """));
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @DisplayName("봉투가 깨졌거나 결과가 없는 응답은 INVALID_AI_RESPONSE로 거절한다")
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
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.INVALID_AI_RESPONSE);
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @DisplayName("잘못된 영상 ID·상태·가이드 구조는 INVALID_AI_RESPONSE로 거절한다")
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
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.INVALID_AI_RESPONSE);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("메타 맵이 null이어도 영상을 null 메타로 요청에 포함한다")
    void sendsNullMetaWhenMetasIsNull() throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":["
                        + "{\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"not_found\",\"error\":null}]}}",
                null,
                0);
        client.analyzeVideo(List.of("a"), null, Duration.ofSeconds(5));

        assertThat(new ObjectMapper().readTree(request.get()))
                .isEqualTo(new ObjectMapper()
                        .readTree("{\"videos\":[{\"video_url\":\"https://youtube.com/shorts/a\","
                                + "\"upload_date\":null,\"title\":null,\"description\":null}]}"));
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
        var response = client.analyzeVideo(List.of("a", "b"), Map.of(), Duration.ofSeconds(5));

        assertThat(response.results().getFirst().status()).isEqualTo(VideoAnalysisResponse.Status.UNKNOWN);
        assertThat(response.results().get(1).status()).isEqualTo(VideoAnalysisResponse.Status.NOT_FOUND);
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
        var response = client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5));

        assertThat(response.results().getFirst().guides().getFirst().type())
                .isEqualTo(VideoAnalysisResponse.GuideType.UNKNOWN);
    }

    @ParameterizedTest
    @DisplayName("오류 본문이 JSON이 아니어도 HTTP 상태와 Retry-After를 보존한다")
    @ValueSource(ints = {301, 400, 401, 422, 429, 500, 502, 503, 504})
    void preservesHttpStatusEvenWhenErrorBodyIsNotJson(int status) throws Exception {
        serve(status, "<html>not-json</html>", "12", 0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.HTTP_ERROR);
        assertThat(exception.statusCode()).isEqualTo(status);
        assertThat(exception.retryAfter()).isEqualTo("12");
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @DisplayName("Retry-After 원문을 Job 재시도 정책에 그대로 넘긴다")
    @ValueSource(strings = {"Wed, 28 Oct 2026 12:00:00 GMT", "invalid-value"})
    void preservesRawRetryAfterForJobRetryPolicy(String header) throws Exception {
        serve(429, "bad-body", header, 0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.retryAfter()).isEqualTo(header);
    }

    @ParameterizedTest
    @DisplayName("헤더와 본문 전체 수신을 Job 남은 시간으로 제한한다")
    @ValueSource(booleans = {false, true})
    @Timeout(5)
    void limitsHeadersAndEntireBodyByRemainingJobTime(boolean slowBody) throws Exception {
        delayBody = slowBody;
        serve(200, "{}", null, 1200);
        long started = System.nanoTime();
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofMillis(300)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.TIMEOUT);
        assertThat(exception.getCause()).isNotNull();
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(1000);
        assertThat(calls.get()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Job 마감이 지나면 서버를 호출하지 않는다")
    void doesNotCallServerAfterJobDeadline() throws Exception {
        serve(200, "{}", null, 0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ZERO));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.TIMEOUT);
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("전송 실패의 원인 예외를 보존한다")
    void preservesTransportFailureCause() throws Exception {
        serve(200, "{}", null, 0);
        server.stop(0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.TRANSPORT_ERROR);
        assertThat(exception.getCause()).isInstanceOf(java.net.ConnectException.class);
    }

    @Test
    @DisplayName("호출 스레드의 인터럽트를 보존한다")
    void preservesCallerInterrupt() throws Exception {
        serve(200, "{}", null, 1200);
        try {
            Thread.currentThread().interrupt();
            var exception = catchThrowableOfType(
                    AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
            assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.INTERRUPTED);
            assertThat(exception.getCause()).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("명시적으로 설정하면 실제 AI 서버 계약을 검증한다")
    @EnabledIfEnvironmentVariable(named = "AI_ANALYSIS_CONTRACT_URL", matches = "https?://.+")
    void verifiesActualServerContractWhenExplicitlyConfigured() {
        var ids = List.of(System.getenv("AI_ANALYSIS_CONTRACT_VIDEO_IDS").split(","));
        client = new VideoAnalysisJobClient(URI.create(System.getenv("AI_ANALYSIS_CONTRACT_URL")), new ObjectMapper());
        var response = client.analyzeVideo(ids, Map.of(), Duration.ofSeconds(130));
        assertThat(response.results().stream()
                        .map(VideoAnalysisResponse.Result::videoId)
                        .collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(new java.util.HashSet<>(ids));
    }

    @Test
    @DisplayName("스프레드시트 예시의 영업시간과 반복된 영상 URL을 해석한다")
    void decodesSpreadsheetExampleIncludingBusinessHoursAndRepeatedVideoUrl() throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("/ai-analysis/spreadsheet-analysis-response.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        serve(200, fixture, null, 0);
        var response = client.analyzeVideo(List.of("aaaa", "bbbb", "cccc", "dddd"), Map.of(), Duration.ofSeconds(5));
        assertThat(response.results()).hasSize(4);
        assertThat(response.results().get(1).guides()).hasSize(2);
        var place = response.results().getFirst().guides().getFirst();
        assertThat(place.name()).isEqualTo("메가커피 성수점");
        assertThat(place.confidence()).isEqualTo(0.95);
        assertThat(place.businessHours()
                        .weeklyHours()
                        .get(java.time.DayOfWeek.MONDAY)
                        .getFirst()
                        .openTime())
                .isEqualTo(java.time.LocalTime.of(8, 0));
        assertThat(place.businessHours()
                        .exceptions()
                        .get(java.time.LocalDate.of(2026, 10, 20))
                        .isEmpty())
                .isTrue();
        assertThat(response.results().get(3).error().code()).isEqualTo("llm_timeout");
        assertThat(response.results().get(1).guides().get(1).type()).isEqualTo(VideoAnalysisResponse.GuideType.EVENT);
    }

    @Test
    @DisplayName("지연된 오류 본문을 읽기 전에 HTTP 실패를 보존한다")
    void preservesHttpFailureBeforeReadingDelayedErrorBody() throws Exception {
        delayBody = true;
        serve(503, "unavailable", "12", 1200);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofMillis(300)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.HTTP_ERROR);
        assertThat(exception.statusCode()).isEqualTo(503);
        assertThat(exception.retryAfter()).isEqualTo("12");
    }

    @ParameterizedTest
    @DisplayName("잘못된 날짜 표기는 INVALID_AI_RESPONSE로 거절한다")
    @MethodSource("invalidDates")
    void rejectsInvalidDateWireValues(String field, String value) throws Exception {
        serve(
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":[{"
                        + "\"video_url\":\"https://youtube.com/shorts/a\",\"status\":\"success\","
                        + "\"error\":null,\"place_type\":\"이벤트\",\"" + field + "\":" + value + "}]}}",
                null,
                0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.INVALID_AI_RESPONSE);
    }

    static java.util.stream.Stream<Arguments> invalidDates() {
        return java.util.stream.Stream.of("event_start_date", "event_end_date")
                .flatMap(field -> java.util.stream.Stream.of("[2026,10,28]", "\"2026-10-28T12:34:56\"", "\"\"")
                        .map(value -> Arguments.of(field, value)));
    }

    @Test
    @DisplayName("즉시 재시도 가능한 HTTP 실패도 재시도하지 않는다")
    void neverRetriesImmediatelyRetryableHttpFailure() throws Exception {
        serve(503, "unavailable", "0", 0);
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.HTTP_ERROR);
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @DisplayName("텍스트 필드에 숫자나 불리언이 오면 INVALID_AI_RESPONSE로 거절한다")
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
        var exception = catchThrowableOfType(
                AiRequestException.class, () -> client.analyzeVideo(List.of("a"), Map.of(), Duration.ofSeconds(5)));
        assertThat(exception.kind()).isEqualTo(AiRequestException.Kind.INVALID_AI_RESPONSE);
    }
}
