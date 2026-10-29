package eightjbbm.keepgo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.recommendation.service.RecommendationCourseService;
import eightjbbm.keepgo.recommendation.service.RecommendationResultMapper;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.util.Coordinate;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecommendationCourseService.class, RecommendationResultMapper.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("추천 코스 저장")
class RecommendationCourseServiceIntegrationTest {

    private static final String SCHEMA =
            "keepgo_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SERVER_URL = serverUrl();
    private static final String JDBC_URL = SERVER_URL + SCHEMA + "?characterEncoding=UTF-8";

    private final OutingGuideRepository outingGuideRepository;
    private final OutingCourseRepository outingCourseRepository;
    private final RecommendationCourseService courseService;
    private final RecommendationResultMapper resultMapper;

    RecommendationCourseServiceIntegrationTest(
            OutingGuideRepository outingGuideRepository,
            OutingCourseRepository outingCourseRepository,
            RecommendationCourseService courseService,
            RecommendationResultMapper resultMapper) {
        this.outingGuideRepository = outingGuideRepository;
        this.outingCourseRepository = outingCourseRepository;
        this.courseService = courseService;
        this.resultMapper = resultMapper;
    }

    @DynamicPropertySource
    static void disposableSchema(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(SERVER_URL, username(), password());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        }
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", RecommendationCourseServiceIntegrationTest::username);
        registry.add("spring.datasource.password", RecommendationCourseServiceIntegrationTest::password);
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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("코스를 저장한 뒤 트랜잭션 밖에서 응답을 만들어도 지연 로딩이 발생하지 않는다")
    void buildsResultOutsideTransaction() {
        var guide = outingGuideRepository.save(
                new OutingPlace("place", "북촌한옥마을", "한옥 골목", "상세 설명", "서울 종로구", new Coordinate(37.5f, 127.0f), null));

        var criteria = OutingCriteria.emptyOutingCriteriaWithMemberId(1L)
                .update(
                        new Coordinate(37.0f, 127.0f),
                        "서울",
                        Instant.parse("2026-11-01T01:00:00Z"),
                        120,
                        Set.of("place"),
                        null);
        var data = new RecommendCoursesResponseV2.SuccessData(
                null,
                90,
                List.of(new RecommendCoursesResponseV2.Course(
                        "북촌 코스",
                        List.of(new RecommendCoursesResponseV2.Place(
                                String.valueOf(guide.getId()), "북촌한옥마을", 7, null, 30, "한옥 골목")),
                        60)));

        List<OutingCourse> courses = courseService.saveCourses(criteria, data);
        // 실제 호출부(RecommendationServiceV2.recommend)는 별도 실행 스레드에서 DTO를 만든다.
        var result = CompletableFuture.supplyAsync(() -> resultMapper.toResult(criteria, courses))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();

        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).getStops()).hasSize(1);
        assertThat(outingCourseRepository.count()).isEqualTo(1);
        assertThat(result.metadata().totalCourseCount()).isEqualTo(1);
        assertThat(result.details().get(0).items()).singleElement().satisfies(stop -> {
            assertThat(stop.name()).isEqualTo("북촌한옥마을");
            assertThat(stop.category()).isEqualTo("place");
            assertThat(stop.durationMinutes()).isEqualTo(7);
            assertThat(stop.lat()).isEqualTo(37.5f);
            assertThat(stop.lng()).isEqualTo(127.0f);
        });
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
        var value = System.getenv(name);
        if (value == null || !value.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException(
                    "Set " + name + " to a disposable server, e.g. jdbc:mysql://127.0.0.1:33306/");
        }

        return value;
    }
}
