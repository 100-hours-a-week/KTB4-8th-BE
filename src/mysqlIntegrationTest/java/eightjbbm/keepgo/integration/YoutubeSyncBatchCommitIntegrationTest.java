package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubesync.BatchAnalysis;
import eightjbbm.keepgo.interestextraction.service.youtubesync.SyncFencedException;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeConnectionChecker;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncBatchWriter;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncService;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncSnapshot;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.exception.YoutubeNotConnectedException;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(YoutubeSyncBatchCommitIntegrationTest.StubConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
        properties = {
            "keepgo.youtube-sync.poll-interval-ms=50",
            "keepgo.youtube-sync.max-concurrent-syncs=3",
            "keepgo.youtube-sync.recent-result-window=2s",
            "keepgo.youtube-sync.fetch-weight=10"
        })
@DisplayName("YouTube 동기화 배치 커밋 통합 테스트")
class YoutubeSyncBatchCommitIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();
    private static final AtomicReference<String> FAIL_WISHLIST_FOR_VIDEO = new AtomicReference<>();

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final YoutubeSyncService syncService;
    private final YoutubeSyncBatchWriter batchWriter;

    YoutubeSyncBatchCommitIntegrationTest(
            @LocalServerPort int port,
            AccessTokenManager accessTokenManager,
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            YoutubeSyncService syncService,
            YoutubeSyncBatchWriter batchWriter) {
        this.port = port;
        this.accessTokenManager = accessTokenManager;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.syncService = syncService;
        this.batchWriter = batchWriter;
    }

    @BeforeEach
    void resetStubsAndData() {
        AI_STUB.reset();
        YOUTUBE.reset();
        FAIL_WISHLIST_FOR_VIDEO.set(null);

        jdbcTemplate.update("DELETE FROM youtube_sync");
        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("배치 하나의 AI 호출이 실패해도 나머지 배치는 위시리스트에 남고 실패 수에 반영된다")
    void isolatesAiFailureToBatch() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.failAnalysisForVideo("v10", 500, "{\"message\":\"failure\",\"data\":{}}");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        JsonNode success = data(events.getLast());
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(20);
        assertThat(success.get("video_failed_count").asInt()).isEqualTo(10);
        assertThat(success.get("progress").asInt()).isEqualTo(100);
        assertThat(wishlistCount(member)).isEqualTo(10);
        assertThat(guideCount()).isEqualTo(10);
        assertThat(inProgressAnalysisCount()).isZero();

        assertThat(progressPayloads(events)).anySatisfy(payload -> {
            assertThat(payload.get("video_analyzed_count").asInt()).isEqualTo(20);
            assertThat(payload.get("video_failed_count").asInt()).isEqualTo(10);
        });
        // 진척도는 배치 커밋 단위로만 움직인다.
        assertThat(progressPayloads(events))
                .allSatisfy(payload -> assertThat(
                                payload.get("video_analyzed_count").asInt() % 10)
                        .isZero());
    }

    @Test
    @DisplayName("위시리스트 반영 오류는 그 배치만 롤백하고 이미 커밋된 배치는 유지한다")
    void rollsBackOnlyFailingBatch() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        FAIL_WISHLIST_FOR_VIDEO.set("v10");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        JsonNode success = data(events.getLast());
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(20);
        assertThat(success.get("video_failed_count").asInt()).isEqualTo(10);
        assertThat(wishlistCount(member)).isEqualTo(10);
        assertThat(guideCount()).isEqualTo(10);
        assertThat(analysisStatusCount("FAILED")).isEqualTo(10);
        assertThat(inProgressAnalysisCount()).isZero();
    }

    @Test
    @DisplayName("같은 동기화의 여러 배치가 같은 가이드를 만들어도 가이드와 위시리스트에 중복이 없다")
    void deduplicatesGuidesAcrossBatches() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.useSharedGuide("공유 가이드", "서울특별시 종로구 공유로 1");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        assertThat(guideCount()).isEqualTo(1);
        assertThat(wishlistCount(member)).isEqualTo(1);
        assertThat(videoAnalysisResultCount()).isEqualTo(20);
        JsonNode success = data(events.getLast());
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(20);
        assertThat(success.get("video_failed_count").asInt()).isZero();
    }

    @Test
    @DisplayName("임베딩 호출이 실패해도 동기화는 성공한다")
    void ignoresEmbeddingFailure() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        AI_STUB.failEmbedding(500, "{\"message\":\"failure\"}");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        assertThat(data(events.getLast()).get("video_failed_count").asInt()).isZero();
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("AI 응답이 먼저 온 배치부터 커밋한다")
    void commitsBatchesInCompletionOrder() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.setAnalysisDelayForVideo("v0", Duration.ofMillis(800));
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(analysisCreatedAt("v10")).isBefore(analysisCreatedAt("v0"));
    }

    @Test
    @DisplayName("AI 분석 호출은 배치끼리 병렬로 보낸다")
    void submitsAnalysisCallsInParallel() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(AI_STUB.maxConcurrentRequests()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("heartbeat가 만료된 동기화의 이전 실행자는 시작도 배치 커밋도 하지 못한다")
    void rejectsWritesAfterHeartbeatExpiry() {
        Member member = newMember();
        YoutubeSyncSnapshot sync = syncService.create(member.getId(), "instance-a", "trace-id");
        syncService.start(sync.id(), "instance-a", 10, 1, 10);

        backdateHeartbeat(sync.id());
        assertThat(syncService.failExpired(Duration.ofSeconds(1))).isEqualTo(1);

        assertThatThrownBy(() -> syncService.start(sync.id(), "instance-a", 10, 1, 10))
                .isInstanceOf(SyncFencedException.class);
        assertThatThrownBy(() -> batchWriter.commit(
                        sync.id(), "instance-a", member.getId(), BatchAnalysis.failed(List.of("v0")), 100))
                .isInstanceOf(SyncFencedException.class);

        assertThat(syncStatusById(sync.id())).isEqualTo("FAILED");
        assertThat(analysisCount()).isZero();
        assertThat(wishlistCount(member)).isZero();
    }

    private SseTestClient open(String token) throws Exception {
        return SseTestClient.open(URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync"), token);
    }

    private List<SseEvent> runToSuccess(String token) throws Exception {
        List<SseEvent> events = new ArrayList<>();
        try (SseTestClient client = open(token)) {
            events.add(client.next(Duration.ofSeconds(5)));
            events.addAll(client.collectUntilClosed(Duration.ofSeconds(20)));
        }
        assertThat(events).extracting(SseEvent::event).endsWith("youtube-sync.success");
        return events;
    }

    private Member newMember() {
        return memberRepository.save(Member.create("배치 커밋 회원"));
    }

    private String token(Member member) {
        return accessTokenManager.issue(member.getId(), List.of("ROLE_USER"));
    }

    private JsonNode data(SseEvent event) {
        return objectMapper.readTree(event.data());
    }

    private List<JsonNode> progressPayloads(List<SseEvent> events) {
        return events.stream()
                .filter(event -> event.event().equals("youtube-sync.progress"))
                .map(this::data)
                .toList();
    }

    private String syncStatus(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM youtube_sync WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                member.getId());
    }

    private String syncStatusById(Long syncId) {
        return jdbcTemplate.queryForObject("SELECT status FROM youtube_sync WHERE id = ?", String.class, syncId);
    }

    private int wishlistCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ? AND deleted_at IS NULL",
                Integer.class,
                member.getId());
    }

    private int guideCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outing_guide", Integer.class);
    }

    private int analysisCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_analysis", Integer.class);
    }

    private int analysisStatusCount(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_analysis WHERE status = ?", Integer.class, status);
    }

    private int inProgressAnalysisCount() {
        return analysisStatusCount("IN_PROGRESS");
    }

    private int videoAnalysisResultCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_analysis_result", Integer.class);
    }

    private Timestamp analysisCreatedAt(String videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT create_datetime FROM video_analysis WHERE video_id = ?", Timestamp.class, videoId);
    }

    private void backdateHeartbeat(Long syncId) {
        jdbcTemplate.update(
                "UPDATE youtube_sync SET heartbeat_at = TIMESTAMPADD(SECOND, -120, UTC_TIMESTAMP(6)) WHERE id = ?",
                syncId);
    }

    private static String[] ids(int from, int to) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < to; i++) {
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

        @Bean
        @Primary
        YoutubeConnectionChecker stubYoutubeConnectionChecker() {
            return memberId -> {
                if (YOUTUBE.isTokenExpired()) {
                    throw new YoutubeNotConnectedException();
                }
            };
        }

        /// 특정 영상의 가이드가 위시리스트에 반영될 때만 오류를 주입한다.
        @Bean
        @Primary
        OutingWishlistSyncService failingWishlistSync(
                OutingWishlist outingWishlist, MemberRepository memberRepository) {
            return new OutingWishlistSyncService(outingWishlist, memberRepository) {
                @Override
                public void sync(Long memberId, List<OutingGuide> analyzedGuides) {
                    String failingVideo = FAIL_WISHLIST_FOR_VIDEO.get();
                    if (failingVideo != null
                            && analyzedGuides.stream()
                                    .anyMatch(guide -> ("스텁 가이드 " + failingVideo).equals(guide.getName()))) {
                        throw new IllegalStateException("테스트용 위시리스트 오류");
                    }
                    super.sync(memberId, analyzedGuides);
                }
            };
        }
    }
}
