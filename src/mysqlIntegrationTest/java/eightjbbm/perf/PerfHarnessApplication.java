package eightjbbm.perf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eightjbbm.keepgo.KeepgoApplication;
import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/// k6 ?�능 측정???�한 ?�행???�플리�??�션.
///
/// ?�제 ?�플리�??�션??MySQL·Flyway·AI HTTP ?�텁�??�께 ?�우�? 측정???�원�?Access Token??/// `build/perf/tokens.json`??기록?�다.
/// YouTube 조회?� ?�동 ?�인?� 고정 지?�을 가�??�텁?�로 바꾼??
public final class PerfHarnessApplication {

    private PerfHarnessApplication() {}

    public static void main(String[] args) throws Exception {
        String dbHost = environment("DB_HOST", "127.0.0.1");
        String dbPort = environment("DB_PORT", "13306");
        String dbName = environment("DB_NAME", "keepgo_perf");
        String dbUser = environment("DB_USERNAME", "root");
        String dbPassword = environment("DB_PASSWORD", "perf-only");
        String serverUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/";
        String jdbcUrl = serverUrl + dbName + "?characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_bin";

        if (Boolean.parseBoolean(environment("PERF_RESET", "true"))) {
            recreateDatabase(serverUrl, dbName, dbUser, dbPassword);
        }

        Path keyDirectory = createKeys(Path.of(environment("PERF_KEY_DIR", "build/perf/keys")));
        Path tokensFile = Path.of(environment("PERF_TOKENS_FILE", "build/perf/tokens.json"));
        int memberCount = Integer.parseInt(environment("PERF_MEMBERS", "120"));
        int videoCount = Integer.parseInt(environment("PERF_VIDEO_COUNT", "30"));
        int pageSize = Integer.parseInt(environment("PERF_VIDEO_PAGE_SIZE", "50"));
        long youtubeDelayMillis = Long.parseLong(environment("PERF_YOUTUBE_DELAY_MS", "200"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        properties.put("spring.datasource.username", dbUser);
        properties.put("spring.datasource.password", dbPassword);
        properties.put("spring.datasource.hikari.maximum-pool-size", environment("PERF_DB_POOL_SIZE", "50"));
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.baseline-on-migrate", "false");
        properties.put("spring.flyway.clean-disabled", "true");
        properties.put("spring.sql.init.mode", "never");
        properties.put("server.port", environment("PERF_SERVER_PORT", "18081"));
        properties.put("management.server.port", environment("PERF_MANAGEMENT_PORT", "18082"));
        properties.put("logging.level.root", "WARN");
        properties.put("logging.level.eightjbbm.keepgo.perf", "INFO");
        properties.put("keepgo.clients.ai.address", environment("AI_SERVER_API_ADDRESS", "http://127.0.0.1:18080"));
        properties.put("keepgo.youtube-sync.max-concurrent-syncs", environment("PERF_MAX_CONCURRENT_SYNCS", "20"));
        properties.put("keepgo.youtube-sync.poll-interval-ms", environment("PERF_POLL_INTERVAL_MS", "1000"));
        properties.put("keepgo.youtube-sync.fetch-weight", environment("PERF_FETCH_WEIGHT", "10"));
        properties.put("keepgo.youtube-sync.recent-result-window", environment("PERF_RECENT_RESULT_WINDOW", "1s"));
        properties.put(
                "keepgo.security.jwt.public-key",
                keyDirectory.resolve("public_key.pem").toUri().toString());
        properties.put(
                "keepgo.security.jwt.private-key",
                keyDirectory.resolve("private_key.pem").toUri().toString());
        properties.put("spring.security.oauth2.client.registration.google-login.client-id", "perf");
        properties.put("spring.security.oauth2.client.registration.google-login.client-secret", "perf");

        // application.yaml보다 ?�선?�도�??�스???�성?�로 고정?�다.
        properties.forEach((key, value) -> System.setProperty(key, value.toString()));
        var application = new SpringApplication(KeepgoApplication.class, PerfHarnessConfiguration.class);
        application.setDefaultProperties(properties);

        ConfigurableApplicationContext context = application.run(args);
        AccessTokenManager accessTokenManager = context.getBean(AccessTokenManager.class);
        MemberRepository memberRepository = context.getBean(MemberRepository.class);

        List<Member> members = new ArrayList<>();
        for (int index = 0; index < memberCount; index++) {
            members.add(Member.create("perf-" + index));
        }
        memberRepository.saveAll(members);

        List<Map<String, Object>> tokens = new ArrayList<>();
        for (Member member : members) {
            tokens.add(Map.of(
                    "id", member.getId(),
                    "token", accessTokenManager.issue(member.getId(), List.of())));
        }

        Files.createDirectories(tokensFile.getParent());
        Files.writeString(
                tokensFile,
                toJson(Map.of(
                        "members",
                        tokens,
                        "conditions",
                        Map.of(
                                "video_count", videoCount,
                                "video_page_size", pageSize,
                                "youtube_delay_ms", youtubeDelayMillis,
                                "member_count", memberCount))),
                StandardCharsets.UTF_8);

        String adminPort = environment("PERF_ADMIN_PORT", "18083");
        startAdminServer(Integer.parseInt(adminPort), context.getBean(JdbcTemplate.class));

        System.out.printf(
                "PERF_HARNESS_READY api=http://127.0.0.1:%s management=http://127.0.0.1:%s admin=http://127.0.0.1:%s tokens=%s members=%d videos=%d%n",
                properties.get("server.port"),
                properties.get("management.server.port"),
                adminPort,
                tokensFile.toAbsolutePath(),
                memberCount,
                videoCount);
    }

    /// k6가 `accepted ??success` ?�간??DB ?�각?�로 계산?????�게 최신 ?�기???�을 ?�출?�다.
    private static void startAdminServer(int port, JdbcTemplate jdbcTemplate) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/syncs/latest", exchange -> {
            try {
                long memberId = Long.parseLong(queryParameter(exchange, "memberId"));
                Map<String, Object> row = jdbcTemplate.queryForMap("""
                        select id, member_id, status, created_at, finished_at,
                               video_target_count, video_analyzed_count, video_failed_count
                        from youtube_sync
                        where member_id = ?
                        order by id desc
                        limit 1
                        """, memberId);
                writeJson(exchange, toJson(syncRow(row)));
            } catch (Exception error) {
                writeJson(exchange, "{\"error\":" + quote(error.getMessage()) + "}", 500);
            }
        });
        server.createContext("/health", exchange -> writeJson(exchange, "{\"status\":\"ok\"}"));
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static Map<String, Object> syncRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("member_id", row.get("member_id"));
        result.put("status", row.get("status"));
        result.put("created_at_ms", epochMillis(row.get("created_at")));
        result.put("finished_at_ms", epochMillis(row.get("finished_at")));
        result.put("video_target_count", row.get("video_target_count"));
        result.put("video_analyzed_count", row.get("video_analyzed_count"));
        result.put("video_failed_count", row.get("video_failed_count"));
        Object createdAt = result.get("created_at_ms");
        Object finishedAt = result.get("finished_at_ms");
        if (createdAt instanceof Number start && finishedAt instanceof Number end) {
            result.put("accepted_to_success_ms", end.longValue() - start.longValue());
        }
        return result;
    }

    private static Long epochMillis(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toEpochMilli();
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }
        return null;
    }

    private static String queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        writeJson(exchange, body, 200);
    }

    private static void writeJson(HttpExchange exchange, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void recreateDatabase(String serverUrl, String name, String user, String password)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
        }
    }

    private static Path createKeys(Path directory) throws Exception {
        Files.createDirectories(directory);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        writePem(
                directory.resolve("public_key.pem"),
                "PUBLIC KEY",
                pair.getPublic().getEncoded());
        writePem(
                directory.resolve("private_key.pem"),
                "PRIVATE KEY",
                pair.getPrivate().getEncoded());
        return directory;
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        Files.writeString(
                path,
                "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n",
                StandardCharsets.US_ASCII);
    }

    private static String toJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> quote(entry.getKey().toString()) + ":" + toJson(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> values.add(toJson(item)));
            return "[" + String.join(",", values) + "]";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return quote(value.toString());
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
