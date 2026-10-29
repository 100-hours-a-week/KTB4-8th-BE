package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.YoutubeSyncProperties;
import eightjbbm.keepgo.interestextraction.service.MemberLikedVideoReader;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.YoutubeVideoAnalyzer;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncCoordinator;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncStore;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.ObjectMapper;

/// 같은 MySQL을 쓰는 조정기 두 개를 서로 다른 인스턴스 ID로 띄워 인스턴스 간 조정을 검증한다.
///
/// HTTP 연결은 애플리케이션 컨텍스트의 조정기(인스턴스 A)가 받고, 동기화 실행은
/// 테스트가 직접 만든 조정기(인스턴스 B)가 맡는다. 두 조정기는 같은 `youtube_sync` 행만 공유한다.
@Import(YoutubeSyncMultiInstanceIntegrationTest.StubConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
        properties = {"keepgo.youtube-sync.poll-interval-ms=50", "keepgo.youtube-sync.instance-id=instance-a"})
@DisplayName("YouTube 동기화 다중 인스턴스 통합 테스트")
class YoutubeSyncMultiInstanceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final YoutubeSyncStore store;
    private final MemberLikedVideoReader memberLikedVideoReader;
    private final YoutubeVideoAnalyzer youtubeVideoAnalyzer;
    private final OutingWishlistSyncService outingWishlistSyncService;

    YoutubeSyncMultiInstanceIntegrationTest(
            @LocalServerPort int port,
            AccessTokenManager accessTokenManager,
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            YoutubeSyncStore store,
            MemberLikedVideoReader memberLikedVideoReader,
            YoutubeVideoAnalyzer youtubeVideoAnalyzer,
            OutingWishlistSyncService outingWishlistSyncService) {
        this.port = port;
        this.accessTokenManager = accessTokenManager;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.store = store;
        this.memberLikedVideoReader = memberLikedVideoReader;
        this.youtubeVideoAnalyzer = youtubeVideoAnalyzer;
        this.outingWishlistSyncService = outingWishlistSyncService;
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
    @DisplayName("다른 인스턴스가 실행한 동기화 하나에 붙어 진척을 받고, 중복 요청에도 동기화는 하나다")
    void attachesToSyncStartedByAnotherInstance() throws Exception {
        YOUTUBE.addPage(videos(ids(12)));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(600));
        Member member = memberRepository.save(Member.create("다중 인스턴스 회원"));
        String token = accessTokenManager.issue(member.getId(), List.of("ROLE_USER"));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            YoutubeSyncCoordinator otherInstance = new YoutubeSyncCoordinator(
                    store,
                    memberLikedVideoReader,
                    YOUTUBE,
                    youtubeVideoAnalyzer,
                    outingWishlistSyncService,
                    executor,
                    new YoutubeSyncProperties("instance-b", 3, 50L, 15_000L, 1_800_000L, 10, Duration.ofMinutes(5)));

            otherInstance.connect(member.getId());

            assertThat(instanceIdOf(member)).isEqualTo("instance-b");
            assertThat(syncCount(member)).isEqualTo(1);

            List<SseEvent> events = new ArrayList<>();
            try (SseTestClient client =
                    SseTestClient.open(URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync"), token)) {
                events.add(client.next(Duration.ofSeconds(5)));

                // 다른 인스턴스의 동기화에 다시 붙어도 새 행이 생기지 않는다.
                otherInstance.connect(member.getId());
                assertThat(syncCount(member)).isEqualTo(1);

                events.addAll(client.collectUntilClosed(Duration.ofSeconds(20)));
            }

            assertThat(events.get(0).event()).isEqualTo("youtube-sync.accepted");
            assertThat(events)
                    .extracting(SseEvent::event)
                    .contains("youtube-sync.progress")
                    .endsWith("youtube-sync.success");
            assertThat(objectMapper
                            .readTree(events.get(events.size() - 1).data())
                            .get("video_target_count")
                            .asInt())
                    .isEqualTo(12);
        }

        assertThat(instanceIdOf(member)).isEqualTo("instance-b");
        assertThat(statusOf(member)).isEqualTo("SUCCEEDED");
        assertThat(syncCount(member)).isEqualTo(1);
        assertThat(wishlistCount(member)).isEqualTo(12);
    }

    private String instanceIdOf(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT instance_id FROM youtube_sync WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                member.getId());
    }

    private String statusOf(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM youtube_sync WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                member.getId());
    }

    private int syncCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM youtube_sync WHERE member_id = ?", Integer.class, member.getId());
    }

    private int wishlistCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ? AND deleted_at IS NULL",
                Integer.class,
                member.getId());
    }

    private static String[] ids(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add("v" + i);
        }
        return ids.toArray(String[]::new);
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
