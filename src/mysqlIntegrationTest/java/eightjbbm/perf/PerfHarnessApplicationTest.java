package eightjbbm.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@DisplayName("성능 하네스 관리 API")
class PerfHarnessApplicationTest {
    private boolean createdBase;
    private boolean createdRejected;
    private Connection connection;
    private JdbcTemplate jdbc;
    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("MYSQL_TEST_SERVER_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException("일회용 MYSQL_TEST_SERVER_URL이 필요합니다");
        }
        connection = DriverManager.getConnection(
                url,
                System.getenv().getOrDefault("MYSQL_TEST_USER", "root"),
                System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("CREATE DATABASE keepgo_perf_313_base");
        createdBase = true;
        connection.setCatalog("keepgo_perf_313_base");
        jdbc.execute("CREATE TABLE member (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE member_seq (next_val BIGINT)");
        jdbc.execute("CREATE TABLE flyway_schema_history (installed_rank INT)");
        jdbc.execute("CREATE TABLE ai_call_slot (id BIGINT, holder VARCHAR(20))");
        jdbc.execute("CREATE TABLE youtube_sync (id BIGINT, status VARCHAR(20), finished_at DATETIME(6))");
        jdbc.execute("CREATE TABLE outing_guide_embedding_outbox (id BIGINT)");
        jdbc.execute("CREATE TABLE work (member_id BIGINT, FOREIGN KEY (member_id) REFERENCES member(id))");
        jdbc.execute("INSERT INTO member VALUES (1)");
        jdbc.execute("INSERT INTO member_seq VALUES (51)");
        jdbc.execute("INSERT INTO flyway_schema_history VALUES (1)");
        jdbc.execute("INSERT INTO ai_call_slot VALUES (1, NULL)");
        jdbc.execute("INSERT INTO work VALUES (1)");
        jdbc.execute("INSERT INTO youtube_sync VALUES (1, 'SUCCEEDED', NOW(6))");
        server = PerfHarnessApplication.startAdminServer(
                0, jdbc, new PerfHarnessConfiguration.FixedYoutubeVideoProvider(30, 50, 0));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        if (connection != null) {
            if (createdBase) {
                jdbc.execute("DROP DATABASE keepgo_perf_313_base");
            }
            if (createdRejected) {
                jdbc.execute("DROP DATABASE keepgo_reset_rejected");
            }
            connection.close();
        }
    }

    @Test
    @DisplayName("초기화는 작업만 지우고 회원과 슬롯 및 시퀀스를 보존한다")
    void resetsWorkOnly() throws Exception {
        HttpResponse<String> response = request("/reset", "POST");
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM youtube_sync", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT id FROM member", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT next_val FROM member_seq", Long.class))
                .isEqualTo(51);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_call_slot", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT @@FOREIGN_KEY_CHECKS", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 작업과 다른 catalog는 초기화하지 않는다")
    void rejectsUnsafeReset() throws Exception {
        jdbc.execute("UPDATE youtube_sync SET status = 'RUNNING'");
        assertThat(request("/reset", "POST").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work", Integer.class))
                .isEqualTo(1);
        jdbc.execute("UPDATE youtube_sync SET status = 'SUCCEEDED'");
        jdbc.execute("CREATE DATABASE keepgo_reset_rejected");
        createdRejected = true;
        jdbc.execute("CREATE TABLE keepgo_reset_rejected.youtube_sync LIKE youtube_sync");
        jdbc.execute(
                "CREATE TABLE keepgo_reset_rejected.outing_guide_embedding_outbox LIKE outing_guide_embedding_outbox");
        jdbc.execute("CREATE TABLE keepgo_reset_rejected.ai_call_slot LIKE ai_call_slot");
        jdbc.execute("INSERT INTO keepgo_reset_rejected.youtube_sync VALUES (2, 'SUCCEEDED', NOW(6))");
        connection.setCatalog("keepgo_reset_rejected");
        assertThat(request("/reset", "POST").statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM youtube_sync", Integer.class))
                .isEqualTo(1);
        connection.setCatalog("keepgo_perf_313_base");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DDL 실패도 HTTP 오류를 응답하고 FK 검사를 복원한다")
    void restoresForeignKeysAfterFailure() throws Exception {
        jdbc.execute("CREATE VIEW invalid_reset_target AS SELECT id FROM member");
        HttpResponse<String> response = request("/reset", "POST");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(new tools.jackson.databind.ObjectMapper()
                        .readTree(response.body())
                        .has("error"))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT @@FOREIGN_KEY_CHECKS", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 fixture와 DB 조회 실패를 HTTP 응답으로 전달한다")
    void respondsToFailures() throws Exception {
        assertThat(request("/fixture", "POST").statusCode()).isEqualTo(400);
        assertThat(request("/fixture?count=31", "POST").statusCode()).isEqualTo(400);
        assertThat(request("/fixture?count=40&shared=true", "POST").statusCode())
                .isEqualTo(200);
        jdbc.execute("DROP TABLE youtube_sync");
        assertThat(request("/state", "GET").statusCode()).isEqualTo(500);
        assertThat(request("/reset", "POST").statusCode()).isEqualTo(500);
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://127.0.0.1:" + server.getAddress().getPort() + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
