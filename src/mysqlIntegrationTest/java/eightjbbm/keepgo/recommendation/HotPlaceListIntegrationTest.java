package eightjbbm.keepgo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import eightjbbm.keepgo.recommendation.controller.RecommendationControllerV3;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.recommendation.service.HotPlaceServiceV2;
import eightjbbm.keepgo.util.Coordinate;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("V3 장소 목록 MySQL 응답 계약")
class HotPlaceListIntegrationTest {
    private static final String SCHEMA =
            "keepgo_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SERVER_URL = serverUrl();
    private static final String JDBC_URL = SERVER_URL + SCHEMA + "?characterEncoding=UTF-8";
    private final OutingGuideRepository repository;
    private MockMvc mvc;

    HotPlaceListIntegrationTest(OutingGuideRepository repository) {
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationControllerV3(new HotPlaceServiceV2(repository)))
                .build();
    }

    @Test
    @DisplayName("DB에 저장된 장소의 이름과 도로명주소를 places 배열로 반환한다")
    void returnsStoredPlaces() throws Exception {
        repository.saveAndFlush(
                new OutingPlace("place", "서울숲", "도심 공원", "입구", "서울 성동구 뚝섬로 273", new Coordinate(37.5f, 127.0f), null));
        MockHttpServletResponse response =
                mvc.perform(get("/api/v3/places/hot")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()))
                .isEqualTo(new ObjectMapper()
                        .readTree("{\"places\":[{\"name\":\"서울숲\",\"address\":\"서울 성동구 뚝섬로 273\"}]}"));
    }

    @Test
    @DisplayName("DB에 장소가 없으면 빈 places 배열을 반환한다")
    void returnsEmptyPlaces() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/api/v3/places/hot")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()))
                .isEqualTo(new ObjectMapper().readTree("{\"places\":[]}"));
    }

    @DynamicPropertySource
    static void disposableSchema(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(SERVER_URL, username(), password());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        }
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", HotPlaceListIntegrationTest::username);
        registry.add("spring.datasource.password", HotPlaceListIntegrationTest::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @AfterAll
    static void dropDisposableSchema() throws SQLException {
        try (var connection = DriverManager.getConnection(SERVER_URL, username(), password());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    private static String serverUrl() {
        return requiredEnvironment("MYSQL_TEST_SERVER_URL");
    }

    private static String username() {
        return System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
    }

    private static String password() {
        return System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || !value.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException(
                    "Set " + name + " to a disposable server, e.g. jdbc:mysql://127.0.0.1:33306/");
        }

        return value;
    }
}
