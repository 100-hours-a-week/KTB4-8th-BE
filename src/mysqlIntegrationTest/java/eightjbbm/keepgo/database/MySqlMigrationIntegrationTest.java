package eightjbbm.keepgo.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@DisplayName("MySQL Flyway 마이그레이션 통합 테스트")
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
    @DisplayName("빈 DB를 Flyway로 만들고 현재 엔티티 매핑을 validate로 확인한다")
    void migratesEmptyDatabaseAndValidatesCurrentEntityMappings() throws Exception {
        flyway().migrate();

        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO member (id, nickname) VALUES (700, '기존 회원 🌸')");
            try (var result = statement.executeQuery("SELECT nickname FROM member WHERE id = 700")) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("기존 회원 🌸");
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
            assertThat(sessionFactory).isNotNull();
        }
    }

    @Test
    @DisplayName("기존 스키마는 명시적 baseline 뒤에만 migrate하고 기존 데이터를 보존한다")
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

        assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class);
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
                assertThat(result.getString(1)).isEqualTo("기존 회원 🌸");
                assertThat(result.getString(2)).isEqualTo("원문 Cafe ");
                assertThat(result.getString(3)).isNull();
                assertThat(result.getString(4)).isEqualTo("SUCCESS");
                assertThat(result.getTimestamp(5)).isNotNull();
                assertThat(result.getString(6)).isNull();
                assertThat(result.getLong(7)).isEqualTo(1001);
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
            assertThat(longs(connection, "SELECT id FROM outing_guide ORDER BY id"))
                    .containsExactly(910L, 920L, 930L, 931L);
            assertThat(
                            strings(
                                    connection,
                                    "SELECT CONCAT(guide_id, ':', representative_id) FROM outing_guide_dedup_map ORDER BY guide_id"))
                    .containsExactly("910:910", "911:910", "912:910", "920:920", "930:930", "931:931");

            assertThat(strings(
                            connection,
                            "SELECT CONCAT(sequence, ':', outing_guide_id) FROM outing_stop"
                                    + " WHERE outing_course_id = 5001 ORDER BY sequence"))
                    .containsExactly("1:910", "2:910", "3:930");

            assertThat(longs(
                            connection,
                            "SELECT video_analysis_id FROM video_analysis_result"
                                    + " WHERE outing_guide_id = 910 ORDER BY video_analysis_id"))
                    .containsExactly(800L, 801L, 802L);
            assertThat(count(connection, "SELECT COUNT(*) FROM video_analysis_result"))
                    .isEqualTo(3);

            assertThat(longs(
                            connection,
                            "SELECT id FROM outing_collection_private WHERE deleted_at IS NULL ORDER BY id"))
                    .containsExactly(950L, 953L);
            assertThat(
                            strings(
                                    connection,
                                    "SELECT DATE_FORMAT(deleted_at, '%Y-%m-%d %H:%i:%s') FROM outing_collection_private WHERE id = 952"))
                    .containsExactly("2026-01-04 09:00:00");
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM outing_collection_private WHERE id = 951 AND deleted_at IS NOT NULL"))
                    .isEqualTo(1);

            assertThat(count(connection, "SELECT COUNT(*) FROM file WHERE id IN (4001, 4002)"))
                    .isEqualTo(2);
            assertThat(strings(
                            connection,
                            "SELECT CONCAT(guide_id, ':', representative_id, ':', COALESCE(image_id, 'null'))"
                                    + " FROM outing_guide_dedup_map WHERE guide_id <> representative_id"
                                    + " ORDER BY guide_id"))
                    .containsExactly("911:910:4002", "912:910:null");

            assertThat(strings(
                            connection,
                            "SELECT DISTINCT collation_name FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name IN ('name', 'address')"))
                    .containsExactly("utf8mb4_0900_bin");
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name IN ('type', 'name', 'address') AND is_nullable = 'YES'"))
                    .isZero();
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.statistics"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND index_name = 'uk_outing_guide_type_name_address'"))
                    .isEqualTo(3);

            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO outing_guide (id, type, name, address)"
                                + " VALUES (9900, 'place', '같은 카페', '서울시 강남구 테헤란로 1')"))
                        .isInstanceOf(SQLIntegrityConstraintViolationException.class);
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO outing_guide (id, type, name, address)"
                                + " VALUES (9901, 'place', NULL, '서울시 강남구 테헤란로 1')"))
                        .isInstanceOf(SQLIntegrityConstraintViolationException.class);
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

        assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class);

        try (var connection = connection()) {
            assertThat(strings(
                            connection,
                            "SELECT CONCAT(guide_id, ':', reason, ':', wishlist_reference_count, ':',"
                                    + " course_stop_reference_count, ':', analysis_reference_count)"
                                    + " FROM outing_guide_dedup_invalid ORDER BY guide_id"))
                    .containsExactly("960:BLANK_NAME:1:1:1", "961:NULL_NAME:0:0:0", "962:BLANK_ADDRESS:0:0:0");
            assertThat(count(connection, "SELECT COUNT(*) FROM outing_guide")).isEqualTo(3);
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.statistics"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND index_name = 'uk_outing_guide_type_name_address'"))
                    .isZero();
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.columns"
                                    + " WHERE table_schema = DATABASE() AND table_name = 'outing_guide'"
                                    + " AND column_name = 'name' AND is_nullable = 'YES'"))
                    .isEqualTo(1);
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
    @DisplayName("지원하지 않는 연결 문자 집합은 migration 전에 거부한다")
    void refusesUnsupportedConnectionBeforeApplyingMigrations() {
        var migration = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .initSql("SET NAMES latin1")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class);
    }

    @Test
    @DisplayName("migration 프로필에서만 Flyway가 실행된다")
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
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.containsBean("flyway")).isFalse();
        });
        try (var connection = connection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member'
                     """)) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(0);
        }

        runner.withPropertyValues("spring.profiles.active=migration").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            var migration = context.getBean(Flyway.class);
            assertThat(migration.info().current()).isNotNull();
            assertThat(migration.info().pending().length).isEqualTo(0);
            assertThat(migration.getConfiguration().isBaselineOnMigrate()).isFalse();
            assertThat(migration.getConfiguration().isCleanDisabled()).isTrue();
            assertThat(context.getBean(MySqlMigrationPreflight.class)).isNotNull();
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
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
