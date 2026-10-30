package eightjbbm.keepgo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eightjbbm.keepgo.embedding.EmbeddingOutboxProperties;
import eightjbbm.keepgo.embedding.service.EmbeddingOutboxPoller;
import eightjbbm.keepgo.embedding.service.EmbeddingOutboxService;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.interestextraction.service.youtubesync.BatchAnalysis;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncBatchWriter;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncService;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(EmbeddingOutboxIntegrationTest.StubConfig.class)
@TestPropertySource(
        properties = {
            // 폴러는 테스트가 pollOnce()로 직접 한 번씩만 실행한다.
            "keepgo.embedding-outbox.poll-interval=3600000",
            "keepgo.embedding-outbox.claim-duration=2s",
            "keepgo.embedding-outbox.max-attempts=3",
            "keepgo.embedding-outbox.backoff=2s,4s"
        })
@DisplayName("임베딩 아웃박스 통합 테스트")
class EmbeddingOutboxIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String INSTANCE_ID = "embedding-outbox-instance";
    private static final AtomicBoolean FAIL_WISHLIST = new AtomicBoolean(false);

    private final MemberRepository memberRepository;
    private final YoutubeSyncService syncService;
    private final YoutubeSyncBatchWriter batchWriter;
    private final EmbeddingOutboxService outboxService;
    private final OutingGuideRepository outingGuideRepository;
    private final EmbeddingOutboxPoller poller;
    private final EmbeddingOutboxProperties properties;
    private final AiServerService aiServerService;
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, TaskScheduler> taskSchedulers;

    EmbeddingOutboxIntegrationTest(
            MemberRepository memberRepository,
            YoutubeSyncService syncService,
            YoutubeSyncBatchWriter batchWriter,
            EmbeddingOutboxService outboxService,
            OutingGuideRepository outingGuideRepository,
            EmbeddingOutboxPoller poller,
            EmbeddingOutboxProperties properties,
            AiServerService aiServerService,
            MeterRegistry meterRegistry,
            JdbcTemplate jdbcTemplate,
            Map<String, TaskScheduler> taskSchedulers) {
        this.memberRepository = memberRepository;
        this.syncService = syncService;
        this.batchWriter = batchWriter;
        this.outboxService = outboxService;
        this.outingGuideRepository = outingGuideRepository;
        this.poller = poller;
        this.properties = properties;
        this.aiServerService = aiServerService;
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.taskSchedulers = taskSchedulers;
    }

    @BeforeEach
    void resetStubsAndData() {
        AI_STUB.reset();
        FAIL_WISHLIST.set(false);

        jdbcTemplate.update("DELETE FROM youtube_sync");
        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    @DisplayName("신규 가이드를 커밋하면 임베딩을 부르지 않고 아웃박스 행만 남긴다")
    void recordsOutboxWithoutCallingEmbedding() {
        Member member = newMember();
        Long syncId = runningSync(member);

        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v1", "가이드 B", "서울시 B로 2"), 100);

        assertThat(guideCount()).isEqualTo(2);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(AI_STUB.embeddingRequestCount()).isZero();
    }

    @Test
    @DisplayName("배치 트랜잭션이 롤백되면 가이드와 아웃박스 행이 함께 남지 않는다")
    void rollsBackOutboxWithBatchTransaction() {
        Member member = newMember();
        Long syncId = runningSync(member);
        FAIL_WISHLIST.set(true);

        assertThatThrownBy(() ->
                        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100))
                .isInstanceOf(IllegalStateException.class);

        assertThat(guideCount()).isZero();
        assertThat(outboxCount()).isZero();
    }

    @Test
    @DisplayName("폴러를 한 번 실행하면 대상 행으로 임베딩을 호출하고 성공한 행은 삭제한다")
    void embedsDueRowsAndDeletesOnSuccess() {
        Member member = newMember();
        Long syncId = runningSync(member);
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v1", "가이드 B", "서울시 B로 2"), 100);

        int processed = poller.pollOnce();

        assertThat(processed).isEqualTo(2);
        assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(2);
        assertThat(outboxCount()).isZero();
    }

    @Test
    @DisplayName("임베딩이 실패하면 backoff만큼 미루고, 최대 횟수 뒤에는 FAILED로 남긴다")
    void appliesBackoffAndLeavesFailedRow() {
        Member member = newMember();
        Long syncId = runningSync(member);
        AI_STUB.failEmbedding(500, "{\"message\":\"failure\"}");
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);

        poller.pollOnce();
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(outboxAttemptCount()).isEqualTo(1);
        assertThat(nextAttemptAt()).isAfter(now());

        makeDue();
        poller.pollOnce();
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(outboxAttemptCount()).isEqualTo(2);
        assertThat(nextAttemptAt()).isAfter(now());

        makeDue();
        poller.pollOnce();
        assertThat(outboxStatus()).isEqualTo("FAILED");
        assertThat(outboxLastError()).contains("Internal Server Error");
        assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(3);

        // 임베딩 최종 실패는 분석 결과와 위시리스트를 바꾸지 않는다.
        assertThat(wishlistCount(member)).isEqualTo(1);
        assertThat(analysisStatus()).isEqualTo("SUCCESS");
        assertThat(outboxCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("폴러 두 개가 동시에 실행돼도 같은 행을 동시에 처리하지 않는다")
    void letsOnlyOnePollerProcessARow() throws Exception {
        Member member = newMember();
        Long syncId = runningSync(member);
        AI_STUB.setEmbeddingDelay(Duration.ofMillis(300));
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);

        EmbeddingOutboxPoller other = otherPoller();
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var first = executor.submit(() -> {
                start.await();
                return poller.pollOnce();
            });
            var second = executor.submit(() -> {
                start.await();
                return other.pollOnce();
            });
            start.countDown();
            int processed = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);

            assertThat(processed).isEqualTo(1);
            assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(1);
            assertThat(outboxCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리권을 잡은 인스턴스가 죽어 임대가 만료되면 다른 폴러가 다시 처리한다")
    void reclaimsRowWhenClaimHolderDies() {
        Member member = newMember();
        Long syncId = runningSync(member);
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);

        // 처리권을 잡은 폴러가 임베딩 도중 죽은 상태를 재현한다.
        jdbcTemplate.update("""
                UPDATE outing_guide_embedding_outbox
                   SET attempt_count = 1,
                       next_attempt_at = TIMESTAMPADD(SECOND, 2, UTC_TIMESTAMP(6))
                 WHERE status = 'PENDING'
                """);

        poller.pollOnce();
        assertThat(AI_STUB.embeddingRequestCount()).isZero();

        jdbcTemplate.update("UPDATE outing_guide_embedding_outbox SET next_attempt_at = UTC_TIMESTAMP(6)");
        poller.pollOnce();

        assertThat(AI_STUB.embeddingRequestCount()).isEqualTo(1);
        assertThat(outboxCount()).isZero();
        assertThat(guideCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("처리권을 잃은 늦은 실행자는 행을 삭제하거나 FAILED로 바꾸지 못한다")
    void ignoresWritesFromExpiredClaimHolder() {
        Member member = newMember();
        Long syncId = runningSync(member);
        batchWriter.commit(syncId, INSTANCE_ID, member.getId(), batch("v0", "가이드 A", "서울시 A로 1"), 100);
        Long guideId = jdbcTemplate.queryForObject("SELECT guide_id FROM outing_guide_embedding_outbox", Long.class);

        assertThat(outboxService.claim(guideId, 0, 300)).isTrue();
        jdbcTemplate.update("UPDATE outing_guide_embedding_outbox SET next_attempt_at = UTC_TIMESTAMP(6)");
        assertThat(outboxService.claim(guideId, 1, 300)).isTrue();

        outboxService.complete(guideId, 1);
        outboxService.markFailed(guideId, 1, "late failure");

        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(outboxAttemptCount()).isEqualTo(2);
        assertThat(outboxLastError()).isNull();
    }

    @Test
    @DisplayName("임베딩 전용 스케줄러를 추가해도 다른 작업용 기본 스케줄러가 따로 남는다")
    void keepsDefaultSchedulerSeparate() {
        assertThat(taskSchedulers).containsKeys("taskScheduler", "embeddingOutboxTaskScheduler");
        assertThat(taskSchedulers.get("taskScheduler")).isNotSameAs(taskSchedulers.get("embeddingOutboxTaskScheduler"));
    }

    private EmbeddingOutboxPoller otherPoller() {
        return new EmbeddingOutboxPoller(
                outboxService, outingGuideRepository, aiServerService, properties, meterRegistry);
    }

    private Member newMember() {
        return memberRepository.save(Member.create("임베딩 아웃박스 회원"));
    }

    private Long runningSync(Member member) {
        var sync = syncService.create(member.getId(), INSTANCE_ID, "trace-id");
        syncService.start(sync.id(), INSTANCE_ID, 1, 1, 10);
        return sync.id();
    }

    private BatchAnalysis batch(String videoId, String name, String address) {
        OutingGuide guide = new OutingPlace("카페", name, "설명", "상세 위치", address, new Coordinate(37.5f, 127.0f), null);
        return new BatchAnalysis(
                List.of(new BatchAnalysis.AnalyzedVideo(videoId, VideoAnalysisStatus.SUCCESS, List.of(guide))));
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE outing_guide_embedding_outbox SET next_attempt_at = UTC_TIMESTAMP(6)");
    }

    private Timestamp now() {
        return jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class);
    }

    private Timestamp nextAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM outing_guide_embedding_outbox", Timestamp.class);
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outing_guide_embedding_outbox", Integer.class);
    }

    private String outboxStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM outing_guide_embedding_outbox", String.class);
    }

    private int outboxAttemptCount() {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM outing_guide_embedding_outbox", Integer.class);
    }

    private String outboxLastError() {
        return jdbcTemplate.queryForObject("SELECT last_error FROM outing_guide_embedding_outbox", String.class);
    }

    private int guideCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outing_guide", Integer.class);
    }

    private int wishlistCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ? AND deleted_at IS NULL",
                Integer.class,
                member.getId());
    }

    private String analysisStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM video_analysis ORDER BY id DESC LIMIT 1", String.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfig {

        @Bean
        @Primary
        OutingWishlistSyncService failingWishlistSync(
                OutingWishlist outingWishlist, MemberRepository memberRepository) {
            return new OutingWishlistSyncService(outingWishlist, memberRepository) {
                @Override
                public void sync(Long memberId, List<OutingGuide> analyzedGuides) {
                    if (FAIL_WISHLIST.get()) {
                        throw new IllegalStateException("테스트용 위시리스트 오류");
                    }
                    super.sync(memberId, analyzedGuides);
                }
            };
        }
    }
}
