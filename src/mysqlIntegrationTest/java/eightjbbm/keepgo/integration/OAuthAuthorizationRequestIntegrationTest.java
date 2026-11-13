package eightjbbm.keepgo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import eightjbbm.keepgo.KeepgoApplication;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("OAuth 인증 요청 공유 통합 테스트")
class OAuthAuthorizationRequestIntegrationTest extends MySqlIntegrationTestSupport {
    private static final AtomicInteger exchanges = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> issuedNonce =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<String> codeVerifier =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final com.nimbusds.jose.jwk.RSAKey KEY = makeKey();
    private static final java.util.concurrent.atomic.AtomicBoolean issueTokens =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final HttpServer OAUTH = startOAuth();
    private final int port;
    private final Environment environment;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private static final boolean REDIS = System.getenv("OAUTH_REDIS_TEST_PORT") != null;
    private ConfigurableApplicationContext second;

    OAuthAuthorizationRequestIntegrationTest(
            @LocalServerPort int port,
            Environment environment,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            tools.jackson.databind.ObjectMapper mapper,
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.port = port;
        this.environment = environment;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.redis = redis;
    }

    @DynamicPropertySource
    static void oauthStub(DynamicPropertyRegistry registry) {
        registry.add("keepgo.security.oauth2.authorization-request.cleanup-interval", () -> "1000");
        if (REDIS) {
            registry.add("keepgo.security.oauth2.authorization-request.provider", () -> "redis");
            registry.add("spring.data.redis.port", () -> System.getenv("OAUTH_REDIS_TEST_PORT"));
        }
        registry.add(
                "spring.security.oauth2.client.registration.google-login.client-authentication-method", () -> "none");
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri", () -> baseUrl() + "/authorize");
        registry.add("spring.security.oauth2.client.provider.google.token-uri", () -> baseUrl() + "/token");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> baseUrl() + "/jwks");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri", () -> baseUrl() + "/userinfo");
    }

    @Test
    @DisplayName("로그인 시작은 요청별 보안 쿠키를 발급한다")
    void issuesBrowserCookie() throws Exception {
        var response = start(port);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=600", "Path=/")
                        .doesNotContain("Domain="));
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
    }

    @Test
    @DisplayName("다른 인스턴스에서 쿠키가 없는 콜백은 소비하지 않고 원래 브라우저만 코드 교환에 도달한다")
    void missingCookieDoesNotConsumeAcrossInstances() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        {
            ConfigurableApplicationContext other = secondInstance();
            int otherPort =
                    ((ServletWebServerApplicationContext) other).getWebServer().getPort();
            var started = start(port);
            String location = started.headers().firstValue("Location").orElseThrow();
            String state = query(URI.create(location)).get("state");
            String cookie = started.headers().allValues("Set-Cookie").stream()
                    .filter(value -> value.startsWith("oauth_req_"))
                    .findFirst()
                    .orElseThrow()
                    .split(";", 2)[0];
            var rejected = callback(otherPort, state, null);
            assertThat(rejected.statusCode()).isEqualTo(302);
            assertThat(exchanges.get()).isZero();
            callback(otherPort, state, cookie.split("=", 2)[0] + "=wrong");
            assertThat(exchanges.get()).isZero();
            var accepted = callback(otherPort, state, cookie);
            assertThat(accepted.statusCode()).isEqualTo(302);
            assertThat(accepted.headers().allValues("Set-Cookie"))
                    .anySatisfy(value -> assertThat(value)
                            .startsWith(cookie.split("=", 2)[0] + "=")
                            .contains("Max-Age=0"));
            assertThat(exchanges.get()).isEqualTo(1);
            callback(port, state, cookie);
            assertThat(exchanges.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("다른 인스턴스의 콜백이 OIDC 인증을 완료하고 기존 성공 계약으로 이동한다")
    void completesOidcAcrossInstances() throws Exception {
        issueTokens.set(true);
        exchanges.set(0);
        {
            ConfigurableApplicationContext other = secondInstance();
            int otherPort =
                    ((ServletWebServerApplicationContext) other).getWebServer().getPort();
            var started = start(port);
            var parameters =
                    query(URI.create(started.headers().firstValue("Location").orElseThrow()));
            issuedNonce.set(parameters.get("nonce"));
            codeVerifier.set(null);
            String cookie = started.headers().allValues("Set-Cookie").stream()
                    .filter(value -> value.startsWith("oauth_req_"))
                    .findFirst()
                    .orElseThrow()
                    .split(";", 2)[0];
            var response = callback(otherPort, parameters.get("state"), cookie);
            assertThat(exchanges.get()).isEqualTo(1);
            assertThat(codeVerifier.get()).isNotBlank();
            String challenge = java.util.Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(codeVerifier.get().getBytes(StandardCharsets.US_ASCII)));
            assertThat(challenge).isEqualTo(parameters.get("code_challenge"));
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow())
                    .contains("#accessToken=", "expiresIn=1800", "initialLogin=");
            String fragment = URI.create(
                            response.headers().firstValue("Location").orElseThrow())
                    .getRawFragment();
            String accessToken =
                    query(URI.create("http://localhost/?" + fragment)).get("accessToken");
            var linkRequest = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/v2/member/external-connections/youtube"))
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            var link = HttpClient.newHttpClient().send(linkRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(link.statusCode()).isEqualTo(200);
            assertThat(link.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
            String linkUrl =
                    mapper.readTree(link.body()).get("authorization_url").asString();
            String linkState = query(URI.create(linkUrl)).get("state");
            String linkCookie = link.headers().allValues("Set-Cookie").stream()
                    .filter(value -> value.startsWith("oauth_req_"))
                    .findFirst()
                    .orElseThrow()
                    .split(";", 2)[0];
            issueTokens.set(false);
            var linkCallback = HttpRequest.newBuilder(URI.create("http://localhost:" + otherPort
                            + "/login/oauth2/code/google?code=test-code&state=" + linkState))
                    .header("Cookie", linkCookie)
                    .GET()
                    .build();
            assertThat(send(linkCallback).statusCode()).isEqualTo(302);
            assertThat(exchanges.get()).isEqualTo(2);
            jdbc.execute("RENAME TABLE oauth_authorization_request TO oauth_authorization_request_offline");
            try {
                var failedLink = HttpClient.newHttpClient().send(linkRequest, HttpResponse.BodyHandlers.ofString());
                assertThat(failedLink.statusCode()).isEqualTo(500);
                assertThat(failedLink.headers().firstValue("Content-Type").orElseThrow())
                        .contains("application/problem+json");
            } finally {
                jdbc.execute("RENAME TABLE oauth_authorization_request_offline TO oauth_authorization_request");
            }
        }
    }

    @Test
    @DisplayName("같은 콜백이 두 인스턴스에 동시에 도착해도 한 번만 코드 교환한다")
    void consumesOnlyOnceAcrossInstances() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        var started = start(port);
        String state = query(URI.create(started.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String cookie = started.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                gate.await();
                return callback(port, state, cookie);
            });
            var second = executor.submit(() -> {
                gate.await();
                return callback(otherPort, state, cookie);
            });
            gate.countDown();
            assertThat(first.get().statusCode()).isEqualTo(302);
            assertThat(second.get().statusCode()).isEqualTo(302);
        }
        assertThat(exchanges.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 탭의 인증 요청은 독립적으로 소비된다")
    void independentRequestsDoNotOverwrite() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        var first = start(port);
        var secondRequest = start(port);
        String firstState = query(
                        URI.create(first.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String secondState = query(URI.create(
                        secondRequest.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String firstCookie = first.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        String secondCookie = secondRequest.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        assertThat(firstCookie).isNotEqualTo(secondCookie);
        callback(otherPort, firstState, firstCookie);
        callback(otherPort, secondState, secondCookie);
        assertThat(exchanges.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("만료된 요청은 정리 작업 전에도 코드 교환을 시작하지 않는다")
    void expiredRequestIsRejectedBeforeCleanup() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        var started = start(port);
        String state = query(URI.create(started.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String cookie = started.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        if (REDIS) {
            redis.opsForHash().put("oauth:authorization-request:" + hash(state), "expires", "1");
        } else {
            jdbc.update(
                    "UPDATE oauth_authorization_request SET expires_at = ? WHERE request_id = ?",
                    java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
                    hash(state));
        }
        assertThat(callback(otherPort, state, cookie).statusCode()).isEqualTo(302);
        assertThat(exchanges.get()).isZero();
    }

    @Test
    @DisplayName("손상되거나 알 수 없는 버전의 요청은 코드 교환을 시작하지 않는다")
    void rejectsCorruptAndUnknownFormats() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        for (String invalid : java.util.List.of("{bad", "{\"version\":2}")) {
            var started = start(port);
            String state = query(
                            URI.create(started.headers().firstValue("Location").orElseThrow()))
                    .get("state");
            String cookie = started.headers().allValues("Set-Cookie").stream()
                    .filter(value -> value.startsWith("oauth_req_"))
                    .findFirst()
                    .orElseThrow()
                    .split(";", 2)[0];
            if (REDIS) {
                redis.opsForHash().put("oauth:authorization-request:" + hash(state), "json", invalid);
            } else {
                jdbc.update(
                        "UPDATE oauth_authorization_request SET request_json = ? WHERE request_id = ?",
                        invalid,
                        hash(state));
            }
            var response = callback(otherPort, state, cookie);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow())
                    .isEqualTo("http://localhost:3000/login");
        }
        assertThat(exchanges.get()).isZero();
    }

    @Test
    @DisplayName("로컬 설정으로 Secure를 명시적으로 끌 수 있다")
    void allowsExplicitInsecureCookieForLocalHttp() throws Exception {
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        assertThat(start(otherPort).headers().allValues("Set-Cookie"))
                .anySatisfy(cookie ->
                        assertThat(cookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure"));
    }

    @Test
    @DisplayName("저장소 장애 시 로그인 시작은 실패 화면으로 이동한다")
    void storageFailureRedirectsAtStart() throws Exception {
        if (REDIS) {
            ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory) redis.getConnectionFactory())
                    .stop();
        } else {
            jdbc.execute("RENAME TABLE oauth_authorization_request TO oauth_authorization_request_offline");
        }
        try {
            var response = start(port);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow())
                    .isEqualTo("http://localhost:3000/login");
            assertThat(response.headers().allValues("Set-Cookie"))
                    .noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
        } finally {
            if (REDIS) {
                ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                                redis.getConnectionFactory())
                        .start();
            } else {
                jdbc.execute("RENAME TABLE oauth_authorization_request_offline TO oauth_authorization_request");
            }
        }
    }

    @Test
    @DisplayName("콜백의 저장소 장애는 코드 교환 없이 실패 화면으로 이동한다")
    void storageFailureRedirectsAtCallback(org.springframework.boot.test.system.CapturedOutput output)
            throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        int otherPort = ((ServletWebServerApplicationContext) secondInstance())
                .getWebServer()
                .getPort();
        var started = start(port);
        String state = query(URI.create(started.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String cookie = started.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        var otherRedis = secondInstance().getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
        if (REDIS) {
            ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                            otherRedis.getConnectionFactory())
                    .stop();
        } else {
            jdbc.execute("RENAME TABLE oauth_authorization_request TO oauth_authorization_request_offline");
        }
        try {
            var response = callback(otherPort, state, cookie);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow())
                    .isEqualTo("http://localhost:3000/login");
            assertThat(exchanges.get()).isZero();
            assertThat(output.getAll())
                    .contains("classification=server", "provider=rdb")
                    .doesNotContain(state, cookie, "test-code");
        } finally {
            if (REDIS) {
                ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                                otherRedis.getConnectionFactory())
                        .start();
            } else {
                jdbc.execute("RENAME TABLE oauth_authorization_request_offline TO oauth_authorization_request");
            }
        }
    }

    @Test
    @DisplayName("만료된 인증 요청 행은 주기적으로 정리된다")
    void expiredRowsAreCleanedUp() throws Exception {
        var started = start(port);
        String state = query(URI.create(started.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String id = hash(state);
        jdbc.update(
                "UPDATE oauth_authorization_request SET expires_at = ? WHERE request_id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
                id);
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
        Integer count;
        do {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oauth_authorization_request WHERE request_id = ?", Integer.class, id);
            if (count != null && count == 0) {
                break;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertThat(count).isZero();
    }

    private static String hash(String value) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @AfterAll
    void closeSecondInstance() {
        jdbc.update(
                "DELETE FROM oauth_account WHERE issuer = ? AND subject = ?",
                "https://accounts.google.com",
                "test-subject");
        if (second != null) {
            second.close();
        }
        OAUTH.stop(0);
    }

    private ConfigurableApplicationContext secondInstance() {
        if (second != null) {
            return second;
        }
        String[] keys = {
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "keepgo.security.jwt.public-key",
            "keepgo.security.jwt.private-key",
            "spring.security.oauth2.client.registration.google.client-id",
            "spring.security.oauth2.client.registration.google.client-secret",
            "spring.security.oauth2.client.registration.google-login.client-id",
            "spring.security.oauth2.client.registration.google-login.client-secret",
            "spring.security.oauth2.client.registration.google-login.client-authentication-method",
            "spring.security.oauth2.client.provider.google.authorization-uri",
            "spring.security.oauth2.client.provider.google.token-uri",
            "spring.security.oauth2.client.provider.google.jwk-set-uri",
            "spring.security.oauth2.client.provider.google.user-info-uri",
            "keepgo.clients.ai.address",
            "keepgo.clients.geocoding.key"
        };
        var properties = Stream.of(keys).collect(Collectors.toMap(key -> key, environment::getProperty));
        if (REDIS) {
            properties.put("keepgo.security.oauth2.authorization-request.provider", "redis");
            properties.put("spring.data.redis.port", System.getenv("OAUTH_REDIS_TEST_PORT"));
        }
        properties.put("keepgo.security.oauth2.authorization-request.secure-cookie", "false");
        properties.put("server.port", "0");
        properties.put("spring.flyway.enabled", "false");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("logging.level.root", "WARN");
        second = new SpringApplicationBuilder(KeepgoApplication.class)
                .initializers(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new org.springframework.core.env.MapPropertySource(
                                "oauth-test", new java.util.HashMap<String, Object>(properties))))
                .run();
        return second;
    }

    private static HttpResponse<Void> start(int port) throws Exception {
        return send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/authorization/google-login"))
                        .GET()
                        .build());
    }

    private static HttpResponse<Void> callback(int port, String state, String cookie) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/login/oauth2/code/google-login?code=test-code&state=" + state))
                .GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return send(builder.build());
    }

    private static HttpResponse<Void> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static Map<String, String> query(URI uri) {
        return Stream.of(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        part -> URLDecoder.decode(part[0], StandardCharsets.UTF_8),
                        part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));
    }

    private static String baseUrl() {
        return "http://localhost:" + OAUTH.getAddress().getPort();
    }

    private static com.nimbusds.jose.jwk.RSAKey makeKey() {
        try {
            return new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                    .keyID("test-key")
                    .generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static HttpServer startOAuth() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(
                    "/jwks",
                    exchange -> respond(exchange, 200, new com.nimbusds.jose.jwk.JWKSet(KEY.toPublicJWK()).toString()));
            server.createContext(
                    "/userinfo",
                    exchange -> respond(
                            exchange,
                            200,
                            "{\"sub\":\"test-subject\",\"name\":\"테스트 회원\",\"email\":\"test@example.com\",\"email_verified\":true}"));
            server.createContext("/token", exchange -> {
                exchanges.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                codeVerifier.set(query(URI.create("http://localhost/?" + body)).get("code_verifier"));
                if (!issueTokens.get()) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
                try {
                    var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                            .issuer("https://accounts.google.com")
                            .subject("test-subject")
                            .audience("integration-test")
                            .issueTime(new java.util.Date())
                            .expirationTime(new java.util.Date(System.currentTimeMillis() + 3600000))
                            .claim("nonce", issuedNonce.get())
                            .claim("name", "테스트 회원")
                            .claim("email", "test@example.com")
                            .build();
                    var jwt = new com.nimbusds.jwt.SignedJWT(
                            new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                                    .keyID(KEY.getKeyID())
                                    .build(),
                            claims);
                    jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(KEY));
                    respond(
                            exchange,
                            200,
                            "{\"access_token\":\"stub-access\",\"token_type\":\"Bearer\","
                                    + "\"expires_in\":3600,\"id_token\":\"" + jwt.serialize() + "\"}");
                } catch (Exception exception) {
                    throw new java.io.IOException(exception);
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("행 잠금을 기다리는 동안 만료된 요청은 코드 교환하지 않는다")
    void expiresWhileWaitingForRowLock() throws Exception {
        issueTokens.set(false);
        exchanges.set(0);
        var started = start(port);
        String state = query(URI.create(started.headers().firstValue("Location").orElseThrow()))
                .get("state");
        String cookie = started.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("oauth_req_"))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
        try (var connection = jdbc.getDataSource().getConnection();
                var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            connection.setAutoCommit(false);
            long connectionId;
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
                result.next();
                connectionId = result.getLong(1);
            }
            try (var lock = connection.prepareStatement(
                    "SELECT request_id FROM oauth_authorization_request WHERE request_id = ? FOR UPDATE")) {
                lock.setString(1, hash(state));
                lock.executeQuery().close();
            }
            var callback = executor.submit(() -> callback(port, state, cookie));
            try {
                long deadline =
                        System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
                int waits;
                do {
                    waits = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID = w.BLOCKING_THREAD_ID WHERE t.PROCESSLIST_ID = ?",
                            Integer.class,
                            connectionId);
                    if (waits > 0) {
                        break;
                    }
                    Thread.sleep(25);
                } while (System.nanoTime() < deadline);
                assertThat(waits).isPositive();
                Thread.sleep(50);
                try (var expire = connection.prepareStatement(
                        "UPDATE oauth_authorization_request SET expires_at = ? WHERE request_id = ?")) {
                    expire.setTimestamp(
                            1, java.sql.Timestamp.from(java.time.Instant.now().minusMillis(10)));
                    expire.setString(2, hash(state));
                    expire.executeUpdate();
                }
                connection.commit();
                assertThat(callback.get().statusCode()).isEqualTo(302);
                assertThat(exchanges.get()).isZero();
            } finally {
                connection.rollback();
            }
        }
    }
}