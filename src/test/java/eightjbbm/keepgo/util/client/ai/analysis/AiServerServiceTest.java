package eightjbbm.keepgo.util.client.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.job.AiRequestException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("AI 서버 임베딩 저장")
class AiServerServiceTest {

    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private volatile String responseBody;
    private HttpServer server;
    private AiServerService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        var restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .configureMessageConverters(
                        converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .build();
        service = new AiServerService(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("/v3/embed-places로 places 없이 정수 place_id 단일 객체를 보내고 success가 true면 성공한다")
    void sendsSinglePlaceObjectToV3() {
        responseBody = "{\"success\":true,\"place_id\":7}";

        assertThatCode(() -> service.embedPlace(guide(7L))).doesNotThrowAnyException();

        assertThat(paths).containsExactly("/v3/embed-places");
        assertThat(bodies.get(0))
                .startsWith("{\"place_id\":7,\"place_name\":")
                .contains("\"summary\":")
                .doesNotContain("places", "\"place_id\":\"");
    }

    @Test
    @DisplayName("success가 false면 실패로 처리한다")
    void failsWhenSuccessFalse() {
        responseBody = "{\"success\":false,\"place_id\":7}";

        assertThatThrownBy(() -> service.embedPlace(guide(7L))).isInstanceOf(AiRequestException.class);
    }

    @Test
    @DisplayName("구계약 응답이면 성공으로 보지 않는다")
    void doesNotFallBackToLegacyResponse() {
        responseBody = "{\"message\":\"embed_success\",\"data\":{\"embedded_count\":1}}";

        assertThatThrownBy(() -> service.embedPlace(guide(7L))).isInstanceOf(AiRequestException.class);
    }

    @Test
    @DisplayName("success 필드가 없으면 실패로 처리한다")
    void failsWhenSuccessMissing() {
        responseBody = "{\"place_id\":7}";

        assertThatThrownBy(() -> service.embedPlace(guide(7L))).isInstanceOf(AiRequestException.class);
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 실패로 처리한다")
    void failsWhenBodyEmpty() {
        responseBody = "";

        assertThatThrownBy(() -> service.embedPlace(guide(7L))).isInstanceOf(AiRequestException.class);
    }

    @Test
    @DisplayName("응답 JSON이 깨졌으면 INVALID_AI_RESPONSE로 실패한다")
    void failsWhenBodyMalformed() {
        responseBody = "{";

        assertThatThrownBy(() -> service.embedPlace(guide(7L))).isInstanceOfSatisfying(AiRequestException.class, e -> {
            assertThat(e.kind()).isEqualTo(AiRequestException.Kind.INVALID_AI_RESPONSE);
            assertThat(e.getCause()).isNotNull();
        });
    }

    @Test
    @DisplayName("실패 예외에 실제 응답 상태 코드를 담는다")
    void keepsActualStatusCode() {
        responseBody = "{\"success\":false,\"place_id\":7}";

        assertThatThrownBy(() -> service.embedPlace(guide(7L)))
                .isInstanceOfSatisfying(
                        AiRequestException.class,
                        e -> assertThat(e.statusCode()).isEqualTo(200));
    }

    private static OutingPlace guide(long id) {
        var guide = new OutingPlace(
                "카페", "성수 A카페", "대화 소음 적음", "상세", "서울특별시 성동구 A로 1", new Coordinate(37.5f, 127.0f), null);
        ReflectionTestUtils.setField(guide, "id", id);
        return guide;
    }
}
