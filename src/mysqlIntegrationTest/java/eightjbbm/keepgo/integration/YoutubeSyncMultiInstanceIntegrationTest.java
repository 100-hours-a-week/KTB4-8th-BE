package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.YoutubeSyncProperties;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.service.MemberLikedVideoReader;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeConnectionChecker;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncBatchProcessor;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncCoordinator;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncService;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
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
        properties = {
            "keepgo.youtube-sync.poll-interval-ms=50",
            "keepgo.youtube-sync.instance-id=instance-a",
            "keepgo.youtube-sync.heartbeat-interval-ms=3600000",
            "keepgo.youtube-sync.sweep-interval-ms=3600000"
        })
@DisplayName("YouTube 동기화 다중 인스턴스 통합 테스트")
class YoutubeSyncMultiInstanceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final YoutubeSyncService store;
    private final MemberLikedVideoReader memberLikedVideoReader;
    private final YoutubeSyncBatchProcessor batchProcessor;
    private final VideoAnalysisManager videoAnalysisManager;

    YoutubeSyncMultiInstanceIntegrationTest(
            @LocalServerPort int port,
            AccessTokenManager accessTokenManager,
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            YoutubeSyncService store,
            MemberLikedVideoReader memberLikedVideoReader,
            YoutubeSyncBatchProcessor batchProcessor,
            VideoAnalysisManager videoAnalysisManager) {
        this.port = port;
        this.accessTokenManager = accessTokenManager;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.store = store;
        this.memberLikedVideoReader = memberLikedVideoReader;
        this.batchProcessor = batchProcessor;
        this.videoAnalysisManager = videoAnalysisManager;
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
                    batchProcessor,
                    memberId -> {},
                    executor,
                    new YoutubeSyncProperties(
                            "instance-b", 3, 15_000L, 1_800_000L, 10, Duration.ofMinutes(5), Duration.ofSeconds(60)));

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

    @Test
    @DisplayName("heartbeat가 끊긴 QUEUED·RUNNING 동기화만 FAILED로 바꾸고, 만료된 회원은 새 동기화를 시작할 수 있다")
    void failsOnlyExpiredSyncsAndFreesMember() {
        Member healthyQueued = memberRepository.save(Member.create("정상 대기 회원"));
        Member healthyRunning = memberRepository.save(Member.create("정상 실행 회원"));
        Member staleRunning = memberRepository.save(Member.create("만료 실행 회원"));
        Member staleQueued = memberRepository.save(Member.create("만료 대기 회원"));

        var executor = new CapturingExecutor();
        YoutubeSyncCoordinator instanceA = coordinator("instance-a", Duration.ofMinutes(5), executor);
        YoutubeSyncCoordinator instanceB = coordinator("instance-b", Duration.ofSeconds(30), executor);

        instanceA.connect(healthyQueued.getId());
        instanceA.connect(healthyRunning.getId());
        instanceB.connect(staleRunning.getId());
        instanceB.connect(staleQueued.getId());
        Long healthyQueuedSyncId = latestSyncId(healthyQueued);
        Long healthyRunningSyncId = latestSyncId(healthyRunning);
        Long staleRunningSyncId = latestSyncId(staleRunning);
        Long staleQueuedSyncId = latestSyncId(staleQueued);
        store.start(healthyRunningSyncId, "instance-a", 10, 1, 10);
        store.start(staleRunningSyncId, "instance-b", 10, 1, 10);

        videoAnalysisManager.start(healthyQueuedSyncId, List.of("healthy-queued-video"));
        videoAnalysisManager.start(healthyRunningSyncId, List.of("healthy-running-video"));
        videoAnalysisManager.start(staleRunningSyncId, List.of("stale-running-video"));
        videoAnalysisManager.start(staleQueuedSyncId, List.of("stale-queued-video"));

        // Hibernate가 쓴 updated_at도 새 native UTC 비교와 같은 시각대인지 확인한다.
        // ±9시간 오차를 잡으면서도 테스트 실행 시간에 흔들리지 않도록 넓게 둔다.
        assertThat(updatedAgeSeconds(healthyQueuedSyncId)).isBetween(-5, 60);

        // sweep 전에는 정상·만료 분석 모두 진행 중이다.
        assertThat(analysisStatusOf(healthyQueuedSyncId)).isEqualTo("IN_PROGRESS");
        assertThat(analysisStatusOf(healthyRunningSyncId)).isEqualTo("IN_PROGRESS");
        assertThat(analysisStatusOf(staleRunningSyncId)).isEqualTo("IN_PROGRESS");
        assertThat(analysisStatusOf(staleQueuedSyncId)).isEqualTo("IN_PROGRESS");

        // heartbeat와 updated_at/event_seq를 UTC 기준 과거로 둬 heartbeat가 무엇을 바꾸는지 관찰한다.
        backdateHeartbeat(healthyQueuedSyncId);
        backdateHeartbeat(healthyRunningSyncId);
        backdateHeartbeat(staleRunningSyncId);
        backdateHeartbeat(staleQueuedSyncId);

        // 정상 QUEUED·RUNNING 행은 A가 heartbeat로 살리고, sweeper는 다른 인스턴스(B)가 소유한 만료 행만 처리한다.
        instanceA.heartbeat();

        assertThat(isHeartbeatRecent(healthyQueuedSyncId)).isTrue();
        assertThat(isHeartbeatRecent(healthyRunningSyncId)).isTrue();
        assertThat(updatedAgeSeconds(healthyQueuedSyncId)).isGreaterThan(100);
        assertThat(updatedAgeSeconds(healthyRunningSyncId)).isGreaterThan(100);
        assertThat(eventSeqOf(healthyQueuedSyncId)).isEqualTo(7);
        assertThat(eventSeqOf(healthyRunningSyncId)).isEqualTo(7);

        instanceA.sweepExpiredSyncs();

        assertThat(statusOf(healthyQueuedSyncId)).isEqualTo("QUEUED");
        assertThat(statusOf(healthyRunningSyncId)).isEqualTo("RUNNING");
        assertThat(eventSeqOf(healthyQueuedSyncId)).isEqualTo(7);
        assertThat(eventSeqOf(healthyRunningSyncId)).isEqualTo(7);
        assertThat(analysisStatusOf(healthyQueuedSyncId)).isEqualTo("IN_PROGRESS");
        assertThat(analysisStatusOf(healthyRunningSyncId)).isEqualTo("IN_PROGRESS");

        assertSwept(staleRunningSyncId);
        assertSwept(staleQueuedSyncId);

        // 같은 sweeper가 다시 돌아도 이미 FAILED인 행은 건드리지 않는다.
        long runningEventSeq = eventSeqOf(staleRunningSyncId);
        long queuedEventSeq = eventSeqOf(staleQueuedSyncId);
        instanceB.sweepExpiredSyncs();
        assertThat(statusOf(staleRunningSyncId)).isEqualTo("FAILED");
        assertThat(eventSeqOf(staleRunningSyncId)).isEqualTo(runningEventSeq);
        assertThat(eventSeqOf(staleQueuedSyncId)).isEqualTo(queuedEventSeq);

        // 최근 결과 창을 지나도록 finished_at을 UTC로 되돌리면 같은 회원이 새 동기화를 시작할 수 있다.
        jdbcTemplate.update(
                "UPDATE youtube_sync SET finished_at = TIMESTAMPADD(SECOND, -60, UTC_TIMESTAMP(6)) WHERE id = ?",
                staleRunningSyncId);
        assertThat(store.findRecentFinished(staleRunning.getId(), Instant.now().minus(Duration.ofSeconds(30))))
                .isEmpty();

        instanceB.connect(staleRunning.getId());

        assertThat(syncCount(staleRunning)).isEqualTo(2);
        assertThat(statusOf(latestSyncId(staleRunning))).isEqualTo("QUEUED");
    }

    private void assertSwept(Long syncId) {
        assertThat(statusOf(syncId)).isEqualTo("FAILED");
        assertThat(errorCodeOf(syncId)).isEqualTo("INTERNAL_ERROR");
        assertThat(finishedAgeSeconds(syncId)).isBetween(0, 5);
        assertThat(eventSeqOf(syncId)).isEqualTo(8);
        assertThat(isUpdatedRecent(syncId)).isTrue();
        assertThat(analysisStatusOf(syncId)).isEqualTo("FAILED");
    }

    private YoutubeSyncCoordinator coordinator(String instanceId, Duration recentResultWindow, Executor executor) {
        return new YoutubeSyncCoordinator(
                store,
                memberLikedVideoReader,
                batchProcessor,
                memberId -> {},
                executor,
                new YoutubeSyncProperties(
                        instanceId, 3, 15_000L, 1_800_000L, 10, recentResultWindow, Duration.ofSeconds(1)));
    }

    private Long latestSyncId(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM youtube_sync WHERE member_id = ? ORDER BY id DESC LIMIT 1", Long.class, member.getId());
    }

    private String statusOf(Long syncId) {
        return jdbcTemplate.queryForObject("SELECT status FROM youtube_sync WHERE id = ?", String.class, syncId);
    }

    private String errorCodeOf(Long syncId) {
        return jdbcTemplate.queryForObject("SELECT error_code FROM youtube_sync WHERE id = ?", String.class, syncId);
    }

    private long eventSeqOf(Long syncId) {
        return jdbcTemplate.queryForObject("SELECT event_seq FROM youtube_sync WHERE id = ?", Long.class, syncId);
    }

    private void backdateHeartbeat(Long syncId) {
        jdbcTemplate.update("""
                UPDATE youtube_sync
                   SET heartbeat_at = TIMESTAMPADD(SECOND, -120, UTC_TIMESTAMP(6)),
                       updated_at = TIMESTAMPADD(SECOND, -120, UTC_TIMESTAMP(6)),
                       event_seq = 7
                 WHERE id = ?
                """, syncId);
    }

    private boolean isHeartbeatRecent(Long syncId) {
        return jdbcTemplate.queryForObject("""
                SELECT heartbeat_at >= TIMESTAMPADD(SECOND, -100, UTC_TIMESTAMP(6))
                  FROM youtube_sync
                 WHERE id = ?
                """, Boolean.class, syncId);
    }

    private boolean isUpdatedRecent(Long syncId) {
        return jdbcTemplate.queryForObject("""
                SELECT updated_at >= TIMESTAMPADD(SECOND, -5, UTC_TIMESTAMP(6))
                  FROM youtube_sync
                 WHERE id = ?
                """, Boolean.class, syncId);
    }

    private int updatedAgeSeconds(Long syncId) {
        return jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, updated_at, UTC_TIMESTAMP(6)) FROM youtube_sync WHERE id = ?",
                Integer.class,
                syncId);
    }

    private int finishedAgeSeconds(Long syncId) {
        return jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, finished_at, UTC_TIMESTAMP(6)) FROM youtube_sync WHERE id = ?",
                Integer.class,
                syncId);
    }

    private String analysisStatusOf(Long syncId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM video_analysis WHERE youtube_sync_id = ?", String.class, syncId);
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

    /// 동기화 실행을 시작하지 않고 QUEUED 상태를 유지하기 위해 작업을 모으기만 한다.
    static final class CapturingExecutor implements Executor {

        private final List<Runnable> tasks = new CopyOnWriteArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
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
