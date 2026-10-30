package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeConnectionChecker;
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
import org.springframework.test.context.TestPropertySource;

/// V2 동기화 API가 V3 조정기를 거쳐 끝난 뒤 204를 반환하는지 실제 HTTP와 MySQL로 검증한다.
@Import(YoutubeSyncDelegationIntegrationTest.StubConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
        properties = {
            "keepgo.youtube-sync.poll-interval-ms=50",
            "keepgo.youtube-sync.max-concurrent-syncs=3",
            "keepgo.youtube-sync.recent-result-window=2s",
            "keepgo.youtube-sync.fetch-weight=10",
            "keepgo.embedding-outbox.poll-interval-ms=3600000"
        })
@DisplayName("YouTube V2 동기화 위임 통합 테스트")
class YoutubeSyncDelegationIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    YoutubeSyncDelegationIntegrationTest(
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

        jdbcTemplate.update("DELETE FROM youtube_sync");
        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("V2 동기화는 조정기에서 끝난 뒤 204를 반환하고 결과를 위시리스트에 반영한다")
    void delegatesToCoordinatorAndReturnsAfterCompletion() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        Member member = newMember();

        HttpResponse<String> response = syncV2(token(member));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(syncCount(member)).isEqualTo(1);
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("최근에 끝난 동기화가 있어도 V2 요청은 새 동기화를 시작한다")
    void startsNewSyncAfterRecentSuccess() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        Member member = newMember();
        String token = token(member);

        assertThat(syncV2(token).statusCode()).isEqualTo(204);
        assertThat(syncCount(member)).isEqualTo(1);

        YOUTUBE.addPage(videos("video-b"));
        assertThat(syncV2(token).statusCode()).isEqualTo(204);

        assertThat(syncCount(member)).isEqualTo(2);
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("V3 동기화가 실행 중일 때 V2 요청은 새 동기화를 만들지 않고 그 종료를 기다린다")
    void attachesToRunningV3Sync() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(800));
        Member member = newMember();
        String token = token(member);

        try (SseTestClient client = SseTestClient.open(syncV3Uri(), token)) {
            SseEvent accepted = client.next(Duration.ofSeconds(5));
            assertThat(accepted.event()).isEqualTo("youtube-sync.accepted");

            HttpResponse<String> response = syncV2(token);

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(syncCount(member)).isEqualTo(1);
            assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
            assertThat(wishlistCount(member)).isEqualTo(2);

            assertThat(client.collectUntilClosed(Duration.ofSeconds(20)))
                    .extracting(SseEvent::event)
                    .endsWith("youtube-sync.success");
        }
    }

    private HttpResponse<String> syncV2(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(syncV2Uri())
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI syncV2Uri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v2/member/youtube-sync");
    }

    private URI syncV3Uri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync");
    }

    private Member newMember() {
        return memberRepository.save(Member.create("V2 동기화 회원"));
    }

    private String token(Member member) {
        return accessTokenManager.issue(member.getId(), List.of("ROLE_USER"));
    }

    private int syncCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM youtube_sync WHERE member_id = ?", Integer.class, member.getId());
    }

    private String syncStatus(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM youtube_sync WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                member.getId());
    }

    private int wishlistCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ? AND deleted_at IS NULL",
                Integer.class,
                member.getId());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfig {

        @Bean
        @Primary
        YoutubeVideoProvider stubYoutubeVideoProvider() {
            return YOUTUBE;
        }

        @Bean
        @Primary
        YoutubeConnectionChecker stubYoutubeConnectionChecker() {
            return memberId -> {};
        }
    }
}
