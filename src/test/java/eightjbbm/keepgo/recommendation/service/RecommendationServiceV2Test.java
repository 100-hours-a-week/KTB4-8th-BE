package eightjbbm.keepgo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eightjbbm.keepgo.recommendation.controller.RecommendationControllerV2;
import eightjbbm.keepgo.recommendation.controller.RecommendationControllerV3;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.recommendation.repository.CaffeineRecommendationJobStore;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.recommendation.service.dto.RecommendOutingCourseResultV2;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import eightjbbm.keepgo.util.client.ai.sse.AiSseClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("코스 추천 AI 스트림 수신")
class RecommendationServiceV2Test {

    private static final String DONE_SUCCESS = "event: done\ndata: {\"message\":\"recommend_success\",\"data\":"
            + "{\"error\":null,\"courses\":[{\"title\":\"성수 산책\",\"match_score\":74,\"total_duration_minutes\":60,"
            + "\"places\":[{\"place_id\":1,\"place_name\":\"A\",\"travel_minutes\":0,\"arrival_time\":\"14:00\","
            + "\"stay_minutes\":60,\"reason\":\"조용함\"}]}]}}\n\n";

    private final CaffeineRecommendationJobStore jobs = new CaffeineRecommendationJobStore();
    private final OutingCriteriaRepository criteriaRepository = mock(OutingCriteriaRepository.class);
    private final RecommendationCandidateSelector candidateSelector = mock(RecommendationCandidateSelector.class);
    private final RecommendationCourseService courseService = mock(RecommendationCourseService.class);
    private final RecommendationResultMapper resultMapper = mock(RecommendationResultMapper.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final CountDownLatch aiConnectionClosed = new CountDownLatch(1);
    private HttpServer ai;
    private AiSseClient sseClient;
    private MockMvc mvc;
    private volatile AiScript script;

    @BeforeEach
    void setUp() throws Exception {
        var mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        ai = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ai.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                script.run(exchange);
            } finally {
                exchange.close();
            }
        });
        ai.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        ai.start();
        sseClient = new AiSseClient(
                URI.create("http://127.0.0.1:" + ai.getAddress().getPort()),
                mapper,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(10));
        var service = new RecommendationServiceV2(
                new AiServerRecommendApiService(sseClient, mapper),
                criteriaRepository,
                candidateSelector,
                resultMapper,
                courseService,
                jobs,
                executor,
                new StaticMessageSource(),
                new AiCallProperties(
                        3,
                        1,
                        Duration.ofSeconds(3),
                        Duration.ofMillis(200),
                        Duration.ofSeconds(1),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500)));
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationControllerV2(service),
                        new RecommendationControllerV3(mock(HotPlaceServiceV2.class), service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        var criteria = OutingCriteria.emptyOutingCriteriaWithMemberId(1L)
                .update(
                        new Coordinate(37.5f, 127.0f),
                        "성수",
                        Instant.parse("2026-11-01T05:00:00Z"),
                        180,
                        Set.of("카페"),
                        "조용한");
        when(criteriaRepository.findById(1L)).thenReturn(Optional.of(criteria));
        var guide = mock(OutingGuide.class);
        when(guide.getId()).thenReturn(1L);
        when(guide.getCoordinate()).thenReturn(new Coordinate(37.5f, 127.0f));
        when(candidateSelector.select(eq(1L), any())).thenReturn(List.of(guide));
        when(courseService.saveCourses(any(), any())).thenReturn(List.of());
        when(resultMapper.toResult(any(), any()))
                .thenReturn(new RecommendOutingCourseResultV2(
                        new RecommendOutingCourseResultV2.OutingCourseMetadata(0, "성수", "2026-11-01T14:00"),
                        List.of(),
                        List.of()));

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        sseClient.close();
        ai.stop(0);
        executor.shutdownNow();
    }

    @Test
    @DisplayName("v3는 status를 전달하고 done의 recommend_success를 성공으로 판정한다")
    void v3RelaysStatusAndSucceeds() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(exchange, "event: status\ndata: {\"stage\":\"retrieving\"}\n\n");
            write(exchange, DONE_SUCCESS);
        };

        var response = events(post("/api/v3/member/recommendation"), "recommendation.success");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(names(response))
                .containsExactly(
                        "outing-course-recommendation.accepted",
                        "outing-course-recommendation.status",
                        "outing-course-recommendation.success");
        assertThat(response.getContentAsString()).contains("{\"stage\":\"retrieving\"}");
        assertThat(requestPath.get()).isEqualTo("/v3/recommend-course/stream");
        assertThat(requestBody.get()).contains("\"place_id\":1,");
        var data = ArgumentCaptor.forClass(RecommendCoursesResponseV2.SuccessData.class);
        verify(courseService).saveCourses(any(), data.capture());
        assertThat(data.getValue().courses().getFirst().matchScore()).isEqualTo(74);
        assertThat(data.getValue().courses().getFirst().places().getFirst().placeId())
                .isEqualTo("1");
    }

    @Test
    @DisplayName("v2는 status를 전달하지 않고 같은 수신을 재사용한다")
    void v2DoesNotRelayStatus() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(exchange, "event: status\ndata: {\"stage\":\"retrieving\"}\n\n");
            write(exchange, DONE_SUCCESS);
        };

        var response = events(post("/api/v2/member/recommendation"), "recommendation.success");

        assertThat(names(response))
                .containsExactly("outing-course-recommendation.accepted", "outing-course-recommendation.success");
    }

    @Test
    @DisplayName("recommend_not_found는 성공, recommend_cancel_accepted는 stopped로 판정한다")
    void judgesNotFoundAndCancelAccepted() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(exchange, "event: done\ndata: {\"message\":\"recommend_not_found\",\"data\":{\"courses\":[]}}\n\n");
        };
        assertThat(names(events(post("/api/v3/member/recommendation"), "recommendation.success")))
                .endsWith("outing-course-recommendation.success");

        script = exchange -> {
            stream(exchange);
            write(
                    exchange,
                    "event: done\ndata: {\"message\":\"recommend_cancel_accepted\",\"data\":{\"courses\":[]}}\n\n");
        };
        assertThat(names(events(post("/api/v3/member/recommendation"), "recommendation.stopped")))
                .endsWith("outing-course-recommendation.stopped");
    }

    @Test
    @DisplayName("recommend_failed는 v3에서 오류 종류별 이벤트로, v2에서는 service-unavailable로 보낸다")
    void judgesRecommendFailed() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(
                    exchange,
                    "event: done\ndata: {\"message\":\"recommend_failed\",\"data\":{\"error\":\"llm_timeout\",\"courses\":[]}}\n\n");
        };

        var v3 = events(post("/api/v3/member/recommendation"), "recommendation.bad-gateway");
        var v2 = events(post("/api/v2/member/recommendation"), "recommendation.service-unavailable");

        assertThat(names(v3)).endsWith("outing-course-recommendation.bad-gateway");
        assertThat(v3.getContentAsString()).contains("\"code\":\"AI_LLM_TIMEOUT\"");
        assertThat(names(v2)).endsWith("outing-course-recommendation.service-unavailable");
        verify(courseService, org.mockito.Mockito.never()).saveCourses(any(), any());
    }

    @Test
    @DisplayName("done 없이 연결이 끊기면 실패로 끝나고 바로 다시 요청할 수 있다")
    void failsWhenConnectionEndsWithoutDone() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(exchange, "event: status\ndata: {\"stage\":\"retrieving\"}\n\n");
        };

        var first = events(post("/api/v3/member/recommendation"), "recommendation.service-unavailable");
        script = exchange -> {
            stream(exchange);
            write(exchange, DONE_SUCCESS);
        };
        var retry = events(post("/api/v3/member/recommendation"), "recommendation.success");

        assertThat(names(first)).endsWith("outing-course-recommendation.service-unavailable");
        assertThat(names(retry)).endsWith("outing-course-recommendation.success");
    }

    @Test
    @DisplayName("스트림 시작 전 AI 오류는 v3에서 HTTP 응답으로, v2에서는 이벤트로 돌려준다")
    void failsBeforeStream() throws Exception {
        script = exchange -> {
            var body = "{\"message\":\"llm_timeout\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(504, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        };

        var v3 = mvc.perform(post("/api/v3/member/recommendation")).andReturn().getResponse();
        awaitJobReleased();
        var v2 = events(post("/api/v2/member/recommendation"), "recommendation.service-unavailable");

        assertThat(v3.getStatus()).isEqualTo(502);
        assertThat(v3.getContentAsString()).doesNotContain("accepted");
        assertThat(names(v2))
                .containsExactly(
                        "outing-course-recommendation.accepted", "outing-course-recommendation.service-unavailable");
    }

    @Test
    @DisplayName("취소하면 stopped를 보내고 AI 스트림 연결을 즉시 닫는다")
    void cancelClosesAiConnection() throws Exception {
        script = exchange -> {
            stream(exchange);
            try {
                while (true) {
                    write(exchange, ": keep-alive\n\n");
                    Thread.sleep(20);
                }
            } catch (IOException | InterruptedException closed) {
                aiConnectionClosed.countDown();
            }
        };
        var started =
                mvc.perform(post("/api/v3/member/recommendation")).andReturn().getResponse();

        var cancellation = mvc.perform(post("/api/v3/member/recommendation/cancellation"))
                .andReturn()
                .getResponse();
        awaitContent(started, "recommendation.stopped");

        assertThat(cancellation.getStatus()).isEqualTo(204);
        assertThat(names(started)).endsWith("outing-course-recommendation.stopped");
        assertThat(aiConnectionClosed.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("스트림 중 AI error 이벤트는 v3에서 bad-gateway 이벤트로 전달한다")
    void relaysAiErrorEventAsBadGateway() throws Exception {
        script = exchange -> {
            stream(exchange);
            write(exchange, "event: status\ndata: {\"stage\":\"retrieving\"}\n\n");
            write(exchange, "event: error\ndata: {\"message\":\"llm_timeout\"}\n\n");
        };

        var response = events(post("/api/v3/member/recommendation"), "recommendation.bad-gateway");

        assertThat(names(response))
                .containsExactly(
                        "outing-course-recommendation.accepted",
                        "outing-course-recommendation.status",
                        "outing-course-recommendation.bad-gateway");
        assertThat(response.getContentAsString()).contains("\"code\":\"AI_LLM_TIMEOUT\"");
    }

    @Test
    @DisplayName("AI 스트림이 열리기 전에 취소하면 HTTP 오류 없이 stopped를 받는다")
    void cancelBeforeAiOpenedReturnsStopped() throws Exception {
        var selecting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(candidateSelector.select(eq(1L), any())).thenAnswer(invocation -> {
            selecting.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var started = CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                return mvc.perform(post("/api/v3/member/recommendation"))
                        .andReturn()
                        .getResponse();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
        assertThat(selecting.await(2, TimeUnit.SECONDS)).isTrue();

        var cancellation = mvc.perform(post("/api/v3/member/recommendation/cancellation"))
                .andReturn()
                .getResponse();
        var response = started.get(2, TimeUnit.SECONDS);
        release.countDown();
        awaitJobReleased();

        assertThat(cancellation.getStatus()).isEqualTo(204);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(names(response)).containsExactly("outing-course-recommendation.stopped");
    }

    @Test
    @DisplayName("AI 스트림이 제한 시간 안에 열리지 않으면 503으로 응답하고 작업을 정리한다")
    void failsWhenAiNotOpenedInTime() throws Exception {
        var release = new CountDownLatch(1);
        when(candidateSelector.select(eq(1L), any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        var response =
                mvc.perform(post("/api/v3/member/recommendation")).andReturn().getResponse();
        release.countDown();

        assertThat(response.getStatus()).isEqualTo(503);
        awaitJobReleased();
    }

    private MockHttpServletResponse events(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String terminal)
            throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        awaitContent(response, terminal);
        awaitJobReleased();
        return response;
    }

    /// 종료 후 작업 정리가 끝나야 다음 요청이 충돌하지 않는다.
    private void awaitJobReleased() throws Exception {
        for (int i = 0; i < 500 && jobs.find(1L).isPresent(); i++) {
            Thread.sleep(10);
        }
        assertThat(jobs.find(1L)).isEmpty();
    }

    private static void awaitContent(MockHttpServletResponse response, String text) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!response.getContentAsString().contains(text)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("이벤트를 받지 못했습니다: " + text + "\n" + response.getContentAsString());
            }
            Thread.sleep(10);
        }
    }

    private static List<String> names(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString()
                .lines()
                .filter(line -> line.startsWith("event:"))
                .map(line -> line.substring(6).strip())
                .toList();
    }

    private static void stream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
    }

    private static void write(HttpExchange exchange, String text) throws IOException {
        OutputStream out = exchange.getResponseBody();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @FunctionalInterface
    private interface AiScript {
        void run(HttpExchange exchange) throws IOException;
    }
}
