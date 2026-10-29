package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@Import(YoutubeSyncIntegrationTest.StubConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("YouTube 동기화 통합 테스트 환경")
class YoutubeSyncIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    YoutubeSyncIntegrationTest(
            @LocalServerPort int port,
            AccessTokenManager accessTokenManager,
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate) {
        this.port = port;
        this.accessTokenManager = accessTokenManager;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void resetStubsAndData() {
        AI_STUB.reset();
        YOUTUBE.reset();

        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("여러 페이지의 YouTube 영상과 AI 스텁 응답으로 위시리스트에 가이드가 추가된다")
    void syncsWishlistFromStubbedYoutubeAndAi() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b")).addPage(videos("video-c"));

        Member member = memberRepository.save(Member.create("통합 테스트 회원"));
        String token = accessTokenManager.issue(member.getId(), List.of("ROLE_USER"));

        HttpResponse<String> response = sync(token);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(YOUTUBE.callCount()).isEqualTo(2);
        assertThat(YOUTUBE.requestedPageTokens()).containsExactly(null, "1");

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
        assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(1);
        assertThat(AI_STUB.requestCount()).isEqualTo(2);

        assertThat(jdbcTemplate.queryForList("SELECT name FROM outing_guide ORDER BY name", String.class))
                .containsExactly("스텁 가이드 video-a", "스텁 가이드 video-b", "스텁 가이드 video-c");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outing_collection_private WHERE deleted_at IS NULL", Integer.class))
                .isEqualTo(3);
    }

    private HttpResponse<String> sync(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync-plain"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfig {

        @Bean
        @Primary
        YoutubeVideoProvider stubYoutubeVideoProvider() {
            return YOUTUBE;
        }
    }
}
