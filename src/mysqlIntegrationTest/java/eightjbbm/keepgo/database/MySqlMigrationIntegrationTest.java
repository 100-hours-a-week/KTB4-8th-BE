package eightjbbm.keepgo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Entity;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MySqlMigrationIntegrationTest {
    private static String serverUrl;
    private static String username;
    private static String password;
    private String schema;
    private String jdbcUrl;

    @BeforeAll
    static void requireDisposableServer() {
        serverUrl = System.getenv("MYSQL_TEST_SERVER_URL");
        if (serverUrl == null || !serverUrl.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException(
                    "Set MYSQL_TEST_SERVER_URL to a disposable server, e.g. jdbc:mysql://127.0.0.1:33306/");
        }
        username = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
    }

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        schema = "keepgo_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(serverUrl, username, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        }
        jdbcUrl = serverUrl + schema + "?characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_bin";
    }

    @AfterEach
    void dropOnlyTestSchema() throws Exception {
        if (schema != null) {
            try (var connection = DriverManager.getConnection(serverUrl, username, password);
                    var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }

    @Test
    void migratesEmptyDatabaseAndValidatesCurrentEntityMappings() throws Exception {
        flyway().migrate();

        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO member (id, nickname) VALUES (700, '기존 회원 🌸')");
            try (var result = statement.executeQuery("SELECT nickname FROM member WHERE id = 700")) {
                result.next();
                assertEquals("기존 회원 🌸", result.getString(1));
            }
        }

        var configuration = new Configuration()
                .setProperty("hibernate.connection.url", jdbcUrl)
                .setProperty("hibernate.connection.username", username)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty(
                        "hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("eightjbbm.keepgo")) {
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        try (var sessionFactory = configuration.buildSessionFactory()) {
            assertNotNull(sessionFactory);
        }
    }

    @Test
    void requiresExplicitBaselineAndPreservesLegacyDataDuringExpansion() throws Exception {
        try (var connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__current_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/legacy-data.sql"));
        }
        var migration = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/test-expansion")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false)
                .baselineVersion("1")
                .cleanDisabled(true)
                .load();

        assertThrows(FlywayException.class, migration::migrate);
        migration.baseline();
        migration.migrate();
        migration.validate();

        try (var connection = connection();
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT m.nickname, g.name, g.address, a.status, w.deleted_at,
                           a.test_note, s.next_val
                    FROM member m
                    JOIN outing_collection_private w ON w.member_id = m.id
                    JOIN outing_guide g ON g.id = w.guide_id
                    JOIN video_analysis_result r ON r.outing_guide_id = g.id
                    JOIN video_analysis a ON a.id = r.video_analysis_id
                    CROSS JOIN member_seq s
                    WHERE m.id = 700 AND g.id = 900 AND a.id = 800 AND w.id = 950
                    """)) {
                result.next();
                assertEquals("기존 회원 🌸", result.getString(1));
                assertEquals("원문 Cafe ", result.getString(2));
                assertEquals(null, result.getString(3));
                assertEquals("SUCCESS", result.getString(4));
                assertNotNull(result.getTimestamp(5));
                assertEquals(null, result.getString(6));
                assertEquals(1001, result.getLong(7));
            }
        }
    }

    @Test
    @DisplayName("중복 가이드를 최소 ID로 합치고 유일성 제약을 적용한다")
    void mergesDuplicateGuidesIntoSmallestIdAndAppliesUniqueKey() throws Exception {
        try (var connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__current_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/guide-duplicates.sql"));
        }
        var migration = flyway();
        migration.baseline();
        migration.migrate();
        migration.validate();

        try (var connection = connection()) {
            assertEquals(List.of(910L, 920L, 930L, 931L), longs(connection, "SELECT id FROM outing_guide ORDER BY id"));
            assertEquals(
                    List.of("910:910", "911:910", "912:910", "920:920", "930:930", "931:931"),
                    strings(
                            connection,
                            "SELECT CONCAT(guide_id, ':', representative_id) FROM outing_guide_dedup_map ORDER BY guide_id"));

            assertEquals(
                    List.of("1:910", "2:910", "3:930"),
                    strings(
                            connection,
                            "SELECT CONCAT(sequence, ':', outing_guide_id) FROM outing_stop"
                                    + " WHERE outing_course_id = 5001 ORDER BY sequence"));

            assertEquals(
                    List.of(800L, 801L, 802L),
                    longs(
                            connection,
                            "SELECT video_analysis_id FROM video_analysis_result"
                                    + " WHERE outing_guide_id = 910 ORDER BY video_analysis_id"));
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM video_analysis_result"));

            assertEquals(
                    List.of(950L, 953L),
                    longs(connection, "SELECT id FROM outing_collection_private WHERE deleted_at IS NULL ORDER BY id"));
            assertEquals(
                    List.of("2026-01-04 09:00:00"),
                    strings(
                            connection,
                            "SELECT DATE_FORMAT(deleted_at, '%Y-%m-%d %H:%i:%s') FROM outing_collection_private WHERE id = 952"));
            assertEquals(
                    1,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM outing_collection_private WHERE id = 951 AND deleted_at IS NOT NULL"));

            assertEquals(2, count(connection, "SELECT COUNT(*) FROM file WHERE id IN (4001, 4002)"));
            assertEquals(
                    List.of("911:910:4002", "912:910:null"),
                    strings(
                            connection,
                            "SELECT CONCAT(guide_id, ':', representative_id, ':', COALESCE(image_id, 'null'))"
                                    + " FROM outing_guide_dedup_map WHERE guide_id <> representative_id"
                                    + " ORDER BY guide_id"));

            assertEquals(
                    List.of("utf8mb4_0900_bin"),
                    strings(
                            connection,
                            "SELECT DISTINCT collation_name FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name IN ('name', 'address')"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name IN ('type', 'name', 'address') AND is_nullable = 'YES'"));
            assertEquals(
                    3,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.statistics"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND index_name = 'uk_outing_guide_type_name_address'"));

            try (var statement = connection.createStatement()) {
                assertThrows(
                        SQLIntegrityConstraintViolationException.class,
                        () -> statement.executeUpdate("INSERT INTO outing_guide (id, type, name, address)"
                                + " VALUES (9900, 'place', '같은 카페', '서울시 강남구 테헤란로 1')"));
                assertThrows(
                        SQLIntegrityConstraintViolationException.class,
                        () -> statement.executeUpdate("INSERT INTO outing_guide (id, type, name, address)"
                                + " VALUES (9901, 'place', NULL, '서울시 강남구 테헤란로 1')"));
                // 바이트 단위로 다르면 같은 키가 아니다. utf8mb4_0900_bin은 NO PAD라 뒤 공백도 구분한다.
                statement.executeUpdate("INSERT INTO outing_guide (id, type, name, address)"
                        + " VALUES (9902, 'place', '같은 카페', '서울시 강남구 테헤란로 1  ')");
            }
        }
    }

    @Test
    @DisplayName("잘못된 가이드 데이터가 있으면 중단하고 대상 ID와 참조 수를 보고한다")
    void stopsGuideDedupMigrationAndReportsUnresolvedRows() throws Exception {
        try (var connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__current_schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO member (id, nickname) VALUES (700, '기존 회원')");
                statement.executeUpdate("INSERT INTO outing_course (id, name, duration_hours, recommendation_score)"
                        + " VALUES (5002, '잘못된 가이드 코스', 1, 1)");
                statement.executeUpdate(
                        "INSERT INTO outing_guide (id, type, name, address) VALUES (960, 'place', '   ', '서울시 중구 1')");
                statement.executeUpdate(
                        "INSERT INTO outing_guide (id, type, name, address) VALUES (961, 'place', NULL, NULL)");
                statement.executeUpdate(
                        "INSERT INTO outing_guide (id, type, name, address) VALUES (962, 'place', '정상 이름', '   ')");
                statement.executeUpdate(
                        "INSERT INTO outing_collection_private (id, member_id, guide_id, type, created_at)"
                                + " VALUES (970, 700, 960, 'PLACE', utc_timestamp(6))");
                statement.executeUpdate(
                        "INSERT INTO outing_stop (outing_course_id, sequence, outing_guide_id) VALUES (5002, 1, 960)");
                statement.executeUpdate("INSERT INTO video_analysis (id, video_id, status, create_datetime)"
                        + " VALUES (800, 'invalid-video', 'SUCCESS', utc_timestamp(6))");
                statement.executeUpdate(
                        "INSERT INTO video_analysis_result (outing_guide_id, video_analysis_id) VALUES (960, 800)");
            }
        }
        var migration = flyway();
        migration.baseline();

        assertThrows(FlywayException.class, migration::migrate);

        try (var connection = connection()) {
            assertEquals(
                    List.of("960:BLANK_NAME:1:1:1", "961:NULL_NAME:0:0:0", "962:BLANK_ADDRESS:0:0:0"),
                    strings(
                            connection,
                            "SELECT CONCAT(guide_id, ':', reason, ':', wishlist_reference_count, ':',"
                                    + " course_stop_reference_count, ':', analysis_reference_count)"
                                    + " FROM outing_guide_dedup_invalid ORDER BY guide_id"));
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM outing_guide"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.statistics"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND index_name = 'uk_outing_guide_type_name_address'"));
            assertEquals(
                    1,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name = 'name' AND is_nullable = 'YES'"));
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    @Test
    void refusesUnsupportedConnectionBeforeApplyingMigrations() {
        var migration = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .initSql("SET NAMES latin1")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        assertThrows(FlywayException.class, migration::migrate);
    }

    @Test
    void onlyExplicitMigrationProfileAppliesMigrations() throws Exception {
        var runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(
                        AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(MySqlMigrationPreflight.class)
                .withPropertyValues(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password);

        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("flyway"));
        });
        try (var connection = connection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member'
                     """)) {
            result.next();
            assertEquals(0, result.getInt(1));
        }

        runner.withPropertyValues("spring.profiles.active=migration").run(context -> {
            assertNull(context.getStartupFailure());
            var migration = context.getBean(Flyway.class);
            assertNotNull(migration.info().current());
            assertEquals(0, migration.info().pending().length);
            assertFalse(migration.getConfiguration().isBaselineOnMigrate());
            assertTrue(migration.getConfiguration().isCleanDisabled());
            assertNotNull(context.getBean(MySqlMigrationPreflight.class));
            assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        });
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private List<Long> longs(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            List<Long> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getLong(1));
            }
            return values;
        }
    }

    private List<String> strings(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
