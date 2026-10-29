package eightjbbm.keepgo.integration;

import eightjbbm.keepgo.integration.stub.AiServerStub;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/// 실제 MySQL과 HTTP 스텁을 함께 쓰는 통합 테스트의 공통 기반.
///
/// 스키마는 테스트 JVM마다 하나만 만들고 Flyway로 현재 마이그레이션을 적용한다. AI 스텁은
/// 실제 HTTP 서버이므로 `keepgo.clients.ai.address`만 바꿔 애플리케이션 코드를 그대로 둔다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.root=WARN", "spring.main.banner-mode=off"})
@Import(MySqlIntegrationTestSupport.SchemaCleanupConfig.class)
public abstract class MySqlIntegrationTestSupport {

    private static final String SERVER_URL = requiredEnvironment("MYSQL_TEST_SERVER_URL");
    private static final String SCHEMA =
            "keepgo_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String JDBC_URL =
            SERVER_URL + SCHEMA + "?characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_bin";
    private static final Path TEST_KEY_DIRECTORY = createTestKeyDirectory();
    private static final Path PUBLIC_KEY = TEST_KEY_DIRECTORY.resolve("public_key.pem");
    private static final Path PRIVATE_KEY = TEST_KEY_DIRECTORY.resolve("private_key.pem");

    protected static final AiServerStub AI_STUB = new AiServerStub();

    static {
        createSchema();
        AI_STUB.start();
    }

    @DynamicPropertySource
    static void datasourceAndStubs(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", MySqlIntegrationTestSupport::username);
        registry.add("spring.datasource.password", MySqlIntegrationTestSupport::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("keepgo.clients.ai.address", AI_STUB::baseUrl);
        registry.add("keepgo.clients.geocoding.key", () -> "integration-test");
        registry.add("keepgo.security.jwt.public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add(
                "keepgo.security.jwt.private-key", () -> PRIVATE_KEY.toUri().toString());
        registry.add("spring.security.oauth2.client.registration.google-login.client-id", () -> "integration-test");
        registry.add("spring.security.oauth2.client.registration.google-login.client-secret", () -> "integration-test");
        registry.add("logging.level.root", () -> "WARN");
    }

    protected static String username() {
        return System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
    }

    protected static String password() {
        return System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
    }

    private static void createSchema() {
        try (Connection connection = DriverManager.getConnection(SERVER_URL, username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        } catch (SQLException exception) {
            throw new IllegalStateException("MySQL 통합 테스트 스키마를 만들지 못했습니다", exception);
        }
    }

    private static void dropSchema() {
        try (Connection connection = DriverManager.getConnection(SERVER_URL, username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        } catch (SQLException ignored) {
            // 종료 시점에는 이미 서버가 없을 수 있다.
        }
    }

    private static Path createTestKeyDirectory() {
        try {
            Path directory = Files.createTempDirectory("keepgo-it-keys");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            writePem(
                    directory.resolve("public_key.pem"),
                    "PUBLIC KEY",
                    keyPair.getPublic().getEncoded());
            writePem(
                    directory.resolve("private_key.pem"),
                    "PRIVATE KEY",
                    keyPair.getPrivate().getEncoded());
            return directory;
        } catch (Exception exception) {
            throw new IllegalStateException("통합 테스트용 JWT 키를 만들지 못했습니다", exception);
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws IOException {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        Files.writeString(
                path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || !value.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException(
                    "Set " + name + " to a disposable server, e.g. jdbc:mysql://127.0.0.1:33306/");
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SchemaCleanupConfig {

        @Bean
        SchemaCleanup schemaCleanup() {
            return new SchemaCleanup();
        }
    }

    static class SchemaCleanup implements DisposableBean {

        @Override
        public void destroy() {
            dropSchema();
        }
    }
}
