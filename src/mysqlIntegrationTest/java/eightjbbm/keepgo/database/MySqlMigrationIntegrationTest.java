package eightjbbm.keepgo.database;

import jakarta.persistence.Entity;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        try (var connection = connection(); var statement = connection.createStatement()) {
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
                .setProperty("hibernate.physical_naming_strategy",
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
        var migration = Flyway.configure().dataSource(jdbcUrl, username, password)
                .locations("classpath:db/test-expansion")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false).baselineVersion("1").cleanDisabled(true).load();

        assertThrows(FlywayException.class, migration::migrate);
        migration.baseline();
        migration.migrate();
        migration.validate();

        try (var connection = connection(); var statement = connection.createStatement()) {
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

    private Flyway flyway() {
        return Flyway.configure().dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false).cleanDisabled(true).load();
    }

    @Test
    void refusesUnsupportedConnectionBeforeApplyingMigrations() {
        var migration = Flyway.configure().dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .initSql("SET NAMES latin1")
                .callbacks(new MySqlMigrationPreflight())
                .baselineOnMigrate(false).cleanDisabled(true).load();

        assertThrows(FlywayException.class, migration::migrate);
    }

    @Test
    void onlyExplicitMigrationProfileAppliesMigrations() throws Exception {
        var runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(MySqlMigrationPreflight.class)
                .withPropertyValues("spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username, "spring.datasource.password=" + password);

        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("flyway"));
        });
        try (var connection = connection(); var statement = connection.createStatement();
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
}
