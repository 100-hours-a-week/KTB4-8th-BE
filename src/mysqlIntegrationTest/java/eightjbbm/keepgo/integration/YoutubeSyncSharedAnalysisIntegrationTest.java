package eightjbbm.keepgo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eightjbbm.keepgo.integration.stub.AiServerStub;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
import eightjbbm.keepgo.interestextraction.service.youtubesync.BatchAnalysis;
import eightjbbm.keepgo.interestextraction.service.youtubesync.SharedVideoAnalysisService;
import eightjbbm.keepgo.interestextraction.service.youtubesync.SharedVideoResult;
import eightjbbm.keepgo.interestextraction.service.youtubesync.SyncFencedException;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncBatchProcessor;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncBatchWriter;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncService;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeSyncSnapshot;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallSlotRepository;
import eightjbbm.keepgo.util.client.ai.gateway.DefaultAiCallGateway;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisJobClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

/// 영상 분석 결과를 회원과 인스턴스를 넘어 공유하는 동작을 실제 MySQL로 검증한다.
///
/// 두 번째 인스턴스는 별도의 AI 호출 게이트웨이와 처리기를 만들어 흉내 낸다. 게이트웨이의 대기열은
/// 인스턴스 안에만 있으므로 두 인스턴스가 공유하는 것은 MySQL뿐이다.
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
        properties = {
            "keepgo.youtube-sync.heartbeat-interval-ms=3600000",
            "keepgo.youtube-sync.sweep-interval-ms=3600000",
            "keepgo.embedding-outbox.poll-interval=3600000"
        })
@DisplayName("YouTube 영상 분석 공유 통합 테스트")
class YoutubeSyncSharedAnalysisIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Pattern VIDEO_ID = Pattern.compile("youtube\\.com/shorts/([A-Za-z0-9_-]+)");
    private static final AiCallProperties PROPERTIES = new AiCallProperties(
            3,
            1,
            Duration.ofSeconds(150),
            Duration.ofMillis(200),
            Duration.ofSeconds(130),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30));

    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final YoutubeSyncService syncService;
    private final YoutubeSyncBatchProcessor processor;
    private final YoutubeSyncBatchWriter batchWriter;
    private final SharedVideoAnalysisService sharedAnalysis;
    private final VideoAnalysisManager videoAnalysisManager;
    private final AiServerService aiServerService;
    private final VideoAnalysisJobClient videoAnalysisJobClient;
    private final AiCallSlotRepository slots;
    private final List<DefaultAiCallGateway> otherInstanceGateways = new ArrayList<>();

    YoutubeSyncSharedAnalysisIntegrationTest(
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate,
            YoutubeSyncService syncService,
            YoutubeSyncBatchProcessor processor,
            YoutubeSyncBatchWriter batchWriter,
            SharedVideoAnalysisService sharedAnalysis,
            VideoAnalysisManager videoAnalysisManager,
            AiServerService aiServerService,
            VideoAnalysisJobClient videoAnalysisJobClient,
            AiCallSlotRepository slots) {
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.syncService = syncService;
        this.processor = processor;
        this.batchWriter = batchWriter;
        this.sharedAnalysis = sharedAnalysis;
        this.videoAnalysisManager = videoAnalysisManager;
        this.aiServerService = aiServerService;
        this.videoAnalysisJobClient = videoAnalysisJobClient;
        this.slots = slots;
    }

    @BeforeEach
    void resetStubsAndData() {
        AI_STUB.reset();
        jdbcTemplate.update("UPDATE ai_call_slot SET holder = null, leased_until = null");
        jdbcTemplate.update("DELETE FROM youtube_sync");
        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @AfterEach
    void shutdownOtherInstances() {
        otherInstanceGateways.forEach(DefaultAiCallGateway::shutdown);
        otherInstanceGateways.clear();
    }

    @Test
    @DisplayName("다른 회원이 끝낸 SUCCESS·NOT_FOUND는 AI 호출 없이 재사용하고 복수 가이드와 각자의 삭제 의사를 지킨다")
    void reusesCompletedResultsOfOtherMembers() {
        AI_STUB.useGuidesForVideo(
                "video-a",
                new AiServerStub.GuideTemplate("장소 후보", "서울특별시 종로구 다중로 1", "장소"),
                new AiServerStub.GuideTemplate("행사 후보", "서울특별시 종로구 다중로 2", "이벤트"));
        AI_STUB.failAnalysisForVideo("video-b", 200, """
                {"message":"analyze_success","data":{"results":[
                  {"video_url":"https://youtube.com/shorts/video-b","status":"not_found","error":null}
                ]}}
                """);
        Member first = newMember();
        Member second = newMember();
        runSync(processor, first, "instance-a", "video-b");
        runSync(processor, first, "instance-a", "video-a");
        jdbcTemplate.update("""
                UPDATE outing_collection_private SET deleted_at = UTC_TIMESTAMP(6)
                WHERE member_id = ? AND guide_id = (SELECT id FROM outing_guide WHERE name = '장소 후보')
                """, first.getId());
        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(2);

        Long syncId = runSync(processor, second, "instance-a", "video-a", "video-b");

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(2);
        assertThat(analysisCount()).isEqualTo(2);
        assertThat(analyzedCount(syncId)).isEqualTo(2);
        assertThat(failedCount(syncId)).isEqualTo(1);
        assertThat(wishlistCount(second)).isEqualTo(2);
        assertThat(wishlistCount(first)).isEqualTo(1);
        assertThat(guideCount()).isEqualTo(2);
        assertThat(videoAnalysisResultCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("기존 30개와 신규 10개를 재동기화하면 신규 10개만 한 번에 분석한다")
    void analyzesOnlyNewVideosOnResync() {
        Member first = newMember();
        Member second = newMember();
        runSync(processor, first, "instance-a", ids(0, 30));
        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(3);

        Long syncId = runSync(processor, second, "instance-a", ids(0, 40));

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(4);
        assertThat(submittedVideoIds()).hasSize(40).doesNotHaveDuplicates();
        assertThat(analyzedCount(syncId)).isEqualTo(40);
        assertThat(failedCount(syncId)).isZero();
        assertThat(wishlistCount(second)).isEqualTo(40);
    }

    @Test
    @DisplayName("진행 중인 분석에 다른 인스턴스의 동기화가 합류해 같은 영상을 다시 보내지 않는다")
    void joinsAnalysisInProgressOnAnotherInstance() throws Exception {
        AI_STUB.setAnalysisDelay(Duration.ofMillis(700));
        Member first = newMember();
        Member second = newMember();
        YoutubeSyncBatchProcessor otherInstance = otherInstanceProcessor();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Long> owner = executor.submit(() -> runSync(processor, first, "instance-a", ids(0, 10)));
            awaitInProgress(10);
            Future<Long> joiner = executor.submit(() -> runSync(otherInstance, second, "instance-b", ids(0, 10)));

            Long ownerSync = owner.get(30, TimeUnit.SECONDS);
            Long joinerSync = joiner.get(30, TimeUnit.SECONDS);

            assertThat(analyzedCount(ownerSync)).isEqualTo(10);
            assertThat(analyzedCount(joinerSync)).isEqualTo(10);
            assertThat(failedCount(joinerSync)).isZero();
        }

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
        assertThat(submittedVideoIds()).hasSize(10).doesNotHaveDuplicates();
        assertThat(analysisCount()).isEqualTo(10);
        assertThat(wishlistCount(first)).isEqualTo(10);
        assertThat(wishlistCount(second)).isEqualTo(10);
    }

    @Test
    @DisplayName("서로 다른 인스턴스가 일부만 겹치는 영상을 동시에 처음 동기화해도 영상별 전송은 한 번뿐이다")
    void doesNotSubmitOverlappingVideosTwiceAcrossInstances() throws Exception {
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        Member first = newMember();
        Member second = newMember();
        YoutubeSyncBatchProcessor otherInstance = otherInstanceProcessor();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Long> firstRun = executor.submit(() -> runSync(processor, first, "instance-a", ids(0, 25)));
            Future<Long> secondRun = executor.submit(() -> runSync(otherInstance, second, "instance-b", ids(10, 35)));

            assertThat(analyzedCount(firstRun.get(30, TimeUnit.SECONDS))).isEqualTo(25);
            assertThat(analyzedCount(secondRun.get(30, TimeUnit.SECONDS))).isEqualTo(25);
        }

        assertThat(submittedVideoIds()).hasSize(35).doesNotHaveDuplicates();
        assertThat(failedCountTotal()).isZero();
        assertThat(wishlistCount(first)).isEqualTo(25);
        assertThat(wishlistCount(second)).isEqualTo(25);
    }

    @Test
    @DisplayName("같은 영상의 분석 소유권 경쟁은 하나만 이기고 나머지는 합류한다")
    void onlyOneOwnerWinsRace() throws Exception {
        int contenders = 8;
        List<Boolean> owned;
        try (var executor = Executors.newFixedThreadPool(contenders)) {
            List<Future<Boolean>> attempts = IntStream.range(0, contenders)
                    .mapToObj(index -> executor.submit(() -> sharedAnalysis
                            .acquireOwnership(1000L + index, "race-video")
                            .owned()))
                    .toList();
            owned = new ArrayList<>();
            for (Future<Boolean> attempt : attempts) {
                owned.add(attempt.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(owned).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(inProgressCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("합류한 분석이 실패하면 현재 참여자는 실패로 집계하고 다음 동기화에서 다시 분석한다")
    void sharesFailureAndRetriesOnNextSync() throws Exception {
        AI_STUB.setAnalysisDelayForVideo("video-a", Duration.ofMillis(700));
        AI_STUB.failAnalysisForVideo("video-a", 500, "{\"message\":\"failure\"}");
        Member first = newMember();
        Member second = newMember();
        Member third = newMember();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Long> owner = executor.submit(() -> runSync(processor, first, "instance-a", "video-a"));
            awaitInProgress(1);
            Future<Long> joiner = executor.submit(() -> runSync(processor, second, "instance-a", "video-a"));

            assertThat(failedCount(owner.get(30, TimeUnit.SECONDS))).isEqualTo(1);
            assertThat(failedCount(joiner.get(30, TimeUnit.SECONDS))).isEqualTo(1);
        }
        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
        assertThat(analysisCount()).isEqualTo(1);
        assertThat(wishlistCount(second)).isZero();

        AI_STUB.reset();
        Long retried = runSync(processor, third, "instance-a", "video-a");

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
        assertThat(failedCount(retried)).isZero();
        assertThat(wishlistCount(third)).isEqualTo(1);
        assertThat(analysisCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("실행 인스턴스의 heartbeat가 끊기면 대기자는 실패로 끝나고 이전 실행자의 늦은 저장은 거부된다")
    void endsWaitingAndRejectsLateWriteWhenOwnerExpires() throws Exception {
        Member owner = newMember();
        Member waiter = newMember();
        Member next = newMember();
        YoutubeSyncSnapshot ownerSync = syncService.create(owner.getId(), "instance-a", "trace-owner");
        syncService.start(ownerSync.id(), "instance-a", 1, 1, 10);
        assertThat(sharedAnalysis.acquireOwnership(ownerSync.id(), "video-a").owned())
                .isTrue();

        try (var executor = Executors.newFixedThreadPool(1)) {
            Future<Long> waiting = executor.submit(() -> runSync(processor, waiter, "instance-b", "video-a"));
            Thread.sleep(500);
            assertThat(waiting.isDone()).isFalse();

            jdbcTemplate.update(
                    "UPDATE youtube_sync SET heartbeat_at = TIMESTAMPADD(SECOND, -120, UTC_TIMESTAMP(6)) WHERE id = ?",
                    ownerSync.id());
            assertThat(syncService.failExpired(Duration.ofSeconds(60))).isEqualTo(1);

            Long waiterSync = waiting.get(30, TimeUnit.SECONDS);
            assertThat(failedCount(waiterSync)).isEqualTo(1);
        }
        assertThat(AI_STUB.analysisRequestCount()).isZero();
        assertThat(analysisStatus("video-a")).isEqualTo("FAILED");

        BatchAnalysis lateResult = new BatchAnalysis(List.of(new BatchAnalysis.AnalyzedVideo(
                "video-a",
                VideoAnalysisStatus.SUCCESS,
                List.of(new OutingPlace(
                        "카페", "늦은 가이드", "설명", "상세", "서울특별시 종로구 지연로 1", new Coordinate(37.5f, 127.0f), null)))));
        assertThatThrownBy(() -> batchWriter.commit(ownerSync.id(), "instance-a", owner.getId(), lateResult, 100))
                .isInstanceOf(SyncFencedException.class);
        assertThat(guideCount()).isZero();
        assertThat(analysisStatus("video-a")).isEqualTo("FAILED");

        Long retried = runSync(processor, next, "instance-a", "video-a");
        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(1);
        assertThat(failedCount(retried)).isZero();
    }

    @Test
    @DisplayName("끝난 동기화에 진행 중으로 남은 분석은 실패로 마감해 대기가 끝난다")
    void failsOrphanedInProgressAnalysisWhileWaiting() throws Exception {
        Member owner = newMember();
        YoutubeSyncSnapshot finished = syncService.create(owner.getId(), "instance-a", "trace-owner");
        syncService.start(finished.id(), "instance-a", 1, 1, 10);
        videoAnalysisManager.start(finished.id(), List.of("video-a"));
        syncService.succeed(finished.id(), "instance-a", 100);

        Map<String, SharedVideoResult> resolved = sharedAnalysis.awaitResolved(List.of("video-a"));

        assertThat(resolved.get("video-a").status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(analysisStatus("video-a")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("소유자가 살아 있어도 합류한 분석이 제한 시간 안에 끝나지 않으면 대기자는 실패로 끝나고 이력은 그대로 둔다")
    void givesUpWaitingWhenOwnerNeverFinishes() throws Exception {
        Member owner = newMember();
        YoutubeSyncSnapshot ownerSync = syncService.create(owner.getId(), "instance-a", "trace-owner");
        syncService.start(ownerSync.id(), "instance-a", 1, 1, 10);
        assertThat(sharedAnalysis.acquireOwnership(ownerSync.id(), "video-a").owned())
                .isTrue();

        Map<String, SharedVideoResult> resolved =
                sharedAnalysis.awaitResolved(List.of("video-a"), Duration.ofSeconds(1));

        assertThat(resolved.get("video-a").status()).isEqualTo(VideoAnalysisStatus.FAILED);
        assertThat(analysisStatus("video-a")).isEqualTo("IN_PROGRESS");
        assertThat(analysisCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 분석의 유일 제약이 아닌 무결성 위반은 합류로 가리지 않고 던진다")
    void rethrowsOtherIntegrityViolationsOnAcquire() {
        assertThatThrownBy(() -> sharedAnalysis.acquireOwnership(1L, "x".repeat(300)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inProgressCount()).isZero();
    }

    @Test
    @DisplayName("재사용 대상은 가장 최근에 끝난 현행 계약의 SUCCESS·NOT_FOUND만 고른다")
    void selectsReusableHistoryDeterministically() {
        insertAnalysis(1, "not-found", "NOT_FOUND", "2026-10-01 00:00:00");
        insertAnalysis(2, "failed-newest", "NOT_FOUND", "2026-10-01 00:00:00");
        insertAnalysis(3, "failed-newest", "FAILED", "2026-10-02 00:00:00");
        insertAnalysis(4, "recovered", "FAILED", "2026-10-01 00:00:00");
        insertAnalysis(5, "recovered", "NOT_FOUND", "2026-10-02 00:00:00");
        insertAnalysis(6, "legacy-success", "SUCCESS", null);
        insertAnalysis(7, "success-without-guides", "SUCCESS", "2026-10-01 00:00:00");
        insertAnalysis(8, "in-progress", "NOT_FOUND", "2026-10-01 00:00:00");
        Member owner = newMember();
        YoutubeSyncSnapshot running = syncService.create(owner.getId(), "instance-a", "trace-owner");
        jdbcTemplate.update("""
                INSERT INTO video_analysis (id, video_id, youtube_sync_id, status, create_datetime)
                VALUES (9, 'in-progress', ?, 'IN_PROGRESS', UTC_TIMESTAMP(6))
                """, running.id());

        Map<String, SharedVideoResult> completed = sharedAnalysis.findCompleted(List.of(
                "not-found",
                "failed-newest",
                "recovered",
                "legacy-success",
                "success-without-guides",
                "in-progress",
                "never-analyzed"));

        assertThat(completed).containsOnlyKeys("not-found", "recovered");
        assertThat(completed.values())
                .allSatisfy(result -> assertThat(result.status()).isEqualTo(VideoAnalysisStatus.NOT_FOUND));
    }

    private Long runSync(YoutubeSyncBatchProcessor target, Member member, String instanceId, String... videoIds) {
        YoutubeSyncSnapshot sync =
                syncService.create(member.getId(), instanceId, UUID.randomUUID().toString());
        syncService.start(sync.id(), instanceId, videoIds.length, videoIds.length, 10);
        target.process(sync.id(), instanceId, member.getId(), YoutubeStub.videos(videoIds), 10);
        syncService.succeed(sync.id(), instanceId, 100);
        return sync.id();
    }

    private YoutubeSyncBatchProcessor otherInstanceProcessor() {
        DefaultAiCallGateway gateway =
                new DefaultAiCallGateway(aiServerService, videoAnalysisJobClient, slots, PROPERTIES);
        gateway.start();
        otherInstanceGateways.add(gateway);
        return new YoutubeSyncBatchProcessor(gateway, batchWriter, videoAnalysisManager, sharedAnalysis);
    }

    private void awaitInProgress(int count) throws InterruptedException {
        for (int attempt = 0; attempt < 100 && inProgressCount() < count; attempt++) {
            Thread.sleep(50);
        }
        assertThat(inProgressCount()).isEqualTo(count);
    }

    private void insertAnalysis(long id, String videoId, String status, String completedAt) {
        jdbcTemplate.update("""
                INSERT INTO video_analysis (id, video_id, youtube_sync_id, status, create_datetime, completed_at)
                VALUES (?, ?, NULL, ?, UTC_TIMESTAMP(6), ?)
                """, id, videoId, status, completedAt);
    }

    private Member newMember() {
        return memberRepository.save(Member.create("공유 분석 회원"));
    }

    private List<String> submittedVideoIds() {
        return AI_STUB.analysisRequestBodies().stream()
                .flatMap(body -> VIDEO_ID.matcher(body).results().map(result -> result.group(1)))
                .toList();
    }

    private int analyzedCount(Long syncId) {
        return jdbcTemplate.queryForObject(
                "SELECT video_analyzed_count FROM youtube_sync WHERE id = ?", Integer.class, syncId);
    }

    private int failedCount(Long syncId) {
        return jdbcTemplate.queryForObject(
                "SELECT video_failed_count FROM youtube_sync WHERE id = ?", Integer.class, syncId);
    }

    private int failedCountTotal() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(video_failed_count), 0) FROM youtube_sync", Integer.class);
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

    private int inProgressCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_analysis WHERE status = 'IN_PROGRESS'", Integer.class);
    }

    private int videoAnalysisResultCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM video_analysis_result", Integer.class);
    }

    private String analysisStatus(String videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM video_analysis WHERE video_id = ? ORDER BY id DESC LIMIT 1", String.class, videoId);
    }

    private static String[] ids(int from, int to) {
        return IntStream.range(from, to).mapToObj(index -> "v" + index).toArray(String[]::new);
    }
}
