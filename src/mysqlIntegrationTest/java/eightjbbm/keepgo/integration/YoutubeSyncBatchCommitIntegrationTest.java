package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.AiServerStub;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisStatus;
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
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.exception.YoutubeNotConnectedException;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
            "keepgo.youtube-sync.fetch-weight=10",
            // 아웃박스 폴러는 별도 티켓의 관찰 대상이므로 이 테스트에서는 돌리지 않는다.
            "keepgo.embedding-outbox.poll-interval=3600000"
        })
@DisplayName("YouTube 동기화 배치 커밋 통합 테스트")
class YoutubeSyncBatchCommitIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();
    private static final AtomicReference<String> FAIL_WISHLIST_FOR_VIDEO = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> WISHLIST_BARRIER = new AtomicReference<>();

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
        WISHLIST_BARRIER.set(null);

        jdbcTemplate.update("DELETE FROM youtube_sync");
        jdbcTemplate.update("DELETE FROM video_analysis_result");
        jdbcTemplate.update("DELETE FROM video_analysis");
        jdbcTemplate.update("DELETE FROM outing_collection_private");
        jdbcTemplate.update("DELETE FROM outing_guide");
        jdbcTemplate.update("DELETE FROM member");
    }

    @AfterEach
    void dropTemporaryConstraint() {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'outing_guide'
                  AND index_name = 'uk_guide_identity'
                """, Integer.class);
        if (exists != null && exists > 0) {
            jdbcTemplate.execute("ALTER TABLE outing_guide DROP INDEX uk_guide_identity");
        }
    }

    @Test
    @DisplayName("이름과 주소가 같은 장소와 행사는 서로 다른 가이드로 저장한다")
    void storesPlaceAndEventSeparately() {
        createGuideIdentityIndex();
        Member member = newMember();
        YoutubeSyncSnapshot sync = syncService.create(member.getId(), "instance-a", "trace-id");
        syncService.start(sync.id(), "instance-a", 2, 2, 10);

        BatchAnalysis batch = new BatchAnalysis(List.of(
                new BatchAnalysis.AnalyzedVideo(
                        "v0", VideoAnalysisStatus.SUCCESS, List.of(place("같은 이름", "서울특별시 종로구 같은로 1"))),
                new BatchAnalysis.AnalyzedVideo(
                        "v1", VideoAnalysisStatus.SUCCESS, List.of(event("같은 이름", "서울특별시 종로구 같은로 1")))));

        batchWriter.commit(sync.id(), "instance-a", member.getId(), batch, 100);

        assertThat(guideCount()).isEqualTo(2);
        assertThat(wishlistCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("두 동기화가 같은 새 가이드를 동시에 저장해도 한 가이드로 수렴하고 AI 호출은 늘지 않는다")
    void concurrentSyncsReuseGuideWithoutExtraAiCalls() throws Exception {
        createGuideIdentityIndex();
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.useSharedGuide("동시 가이드", "서울특별시 종로구 동시로 1");
        Member first = newMember();
        Member second = newMember();
        WISHLIST_BARRIER.set(new CountDownLatch(2));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<SseEvent>> firstRun = executor.submit(() -> runToSuccess(token(first)));
            Future<List<SseEvent>> secondRun = executor.submit(() -> runToSuccess(token(second)));
            firstRun.get(30, TimeUnit.SECONDS);
            secondRun.get(30, TimeUnit.SECONDS);
        } finally {
            WISHLIST_BARRIER.set(null);
        }

        assertThat(guideCount()).isEqualTo(1);
        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(2);
        assertThat(wishlistCount(first)).isEqualTo(1);
        assertThat(wishlistCount(second)).isEqualTo(1);
    }

    @Test
    @DisplayName("충돌 재시도 때 앞선 시도에서 저장하려던 다른 새 가이드도 함께 저장한다")
    void retrySavesOtherNewGuidesOfRolledBackAttempt() throws Exception {
        createGuideIdentityIndex();
        Member first = newMember();
        Member second = newMember();
        YoutubeSyncSnapshot firstSync = syncService.create(first.getId(), "instance-a", "trace-a");
        YoutubeSyncSnapshot secondSync = syncService.create(second.getId(), "instance-b", "trace-b");
        syncService.start(firstSync.id(), "instance-a", 1, 1, 10);
        syncService.start(secondSync.id(), "instance-b", 1, 1, 10);
        BatchAnalysis firstBatch = new BatchAnalysis(List.of(new BatchAnalysis.AnalyzedVideo(
                "v0", VideoAnalysisStatus.SUCCESS, List.of(place("충돌 가이드", "서울특별시 종로구 충돌로 1")))));
        BatchAnalysis secondBatch = new BatchAnalysis(List.of(new BatchAnalysis.AnalyzedVideo(
                "v1",
                VideoAnalysisStatus.SUCCESS,
                List.of(place("충돌 가이드", "서울특별시 종로구 충돌로 1"), place("새 가이드", "서울특별시 종로구 새로 1")))));
        WISHLIST_BARRIER.set(new CountDownLatch(2));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstRun = executor.submit(
                    () -> batchWriter.commit(firstSync.id(), "instance-a", first.getId(), firstBatch, 100));
            Future<?> secondRun = executor.submit(
                    () -> batchWriter.commit(secondSync.id(), "instance-b", second.getId(), secondBatch, 100));
            firstRun.get(30, TimeUnit.SECONDS);
            secondRun.get(30, TimeUnit.SECONDS);
        } finally {
            WISHLIST_BARRIER.set(null);
        }

        assertThat(guideCount()).isEqualTo(2);
        assertThat(wishlistCount(first)).isEqualTo(1);
        assertThat(wishlistCount(second)).isEqualTo(2);
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
    @DisplayName("영상 하나의 여러 가이드를 모두 저장하고 위시리스트와 분석 이력에 연결한다")
    void storesMultipleGuidesFromOneVideo() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.useGuidesForVideo(
                "video-a",
                new AiServerStub.GuideTemplate("장소 후보", "서울특별시 종로구 다중로 1", "장소"),
                new AiServerStub.GuideTemplate("행사 후보", "서울특별시 종로구 다중로 2", "이벤트"));
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(guideCount()).isEqualTo(2);
        assertThat(wishlistCount(member)).isEqualTo(2);
        assertThat(videoAnalysisResultCount()).isEqualTo(2);
        assertThat(analysisStatusCount("SUCCESS")).isEqualTo(1);
    }

    @Test
    @DisplayName("저장할 수 없는 후보는 제외하고 유효한 후보와 제외 사유를 남긴다")
    void excludesInvalidCandidatesAndRecordsReasons() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.useGuidesForVideo(
                "video-a",
                new AiServerStub.GuideTemplate("정상 장소", "서울특별시 종로구 정상로 1", "장소"),
                new AiServerStub.GuideTemplate(" ", "서울특별시 종로구 공백로 2", "장소"));
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(guideCount()).isEqualTo(1);
        assertThat(wishlistCount(member)).isEqualTo(1);
        assertThat(analysisStatus("video-a")).isEqualTo("SUCCESS");
        JsonNode excluded = excludedCandidates("video-a");
        assertThat(excluded).hasSize(1);
        assertThat(excluded.get(0).get("index").asInt()).isEqualTo(1);
        assertThat(excluded.get(0).get("reason").asText()).isEqualTo("NAME_MISSING");
    }

    @Test
    @DisplayName("모든 후보가 제외된 영상은 INVALID_GUIDE_DATA로 기록하고 동기화는 성공한다")
    void marksInvalidGuideDataWhenAllCandidatesAreExcluded() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.useGuidesForVideo("video-a", new AiServerStub.GuideTemplate("", "주소", "장소"));
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(analysisStatus("video-a")).isEqualTo("FAILED");
        assertThat(failureCode("video-a")).isEqualTo("INVALID_GUIDE_DATA");
        JsonNode excluded = excludedCandidates("video-a");
        assertThat(excluded).hasSize(1);
        assertThat(excluded.get(0).get("reason").asText()).isEqualTo("NAME_MISSING");
        assertThat(guideCount()).isZero();
        assertThat(wishlistCount(member)).isZero();
        assertThat(data(events.getLast()).get("video_failed_count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("구조 오류가 난 배치만 INVALID_AI_RESPONSE로 실패하고 다른 배치는 유지된다")
    void failsOnlyBatchWithInvalidAiResponse() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.failAnalysisForVideo("v10", 200, "{\"message\":\"analyze_success\",\"data\":{\"results\":[]}}");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        assertThat(guideCount()).isEqualTo(10);
        assertThat(wishlistCount(member)).isEqualTo(10);
        assertThat(analysisStatusCount("FAILED")).isEqualTo(10);
        assertThat(failureCode("v10")).isEqualTo("INVALID_AI_RESPONSE");
        assertThat(data(events.getLast()).get("video_analyzed_count").asInt()).isEqualTo(20);
        assertThat(data(events.getLast()).get("video_failed_count").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("알 수 없는 상태는 그 영상만 실패시키고 같은 배치의 다른 영상은 저장한다")
    void failsOnlyUnknownStatusVideo() throws Exception {
        YOUTUBE.addPage(videos("v0", "v1"));
        AI_STUB.failAnalysisForVideo(
                "v0",
                200,
                "{\"message\":\"analyze_success\",\"data\":{\"results\":["
                        + "{\"video_url\":\"https://youtube.com/shorts/v0\",\"status\":\"UNKNOWN\",\"error\":null},"
                        + "{\"video_url\":\"https://youtube.com/shorts/v1\",\"status\":\"success\",\"error\":null,"
                        + "\"place_type\":\"장소\",\"place_name\":\"정상 장소\",\"address\":\"서울특별시 종로구 정상로 1\","
                        + "\"lat\":37.5,\"lng\":127.0}]}}");
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(analysisStatus("v0")).isEqualTo("FAILED");
        assertThat(failureCode("v0")).isEqualTo("INVALID_AI_RESPONSE");
        assertThat(analysisStatus("v1")).isEqualTo("SUCCESS");
        assertThat(guideCount()).isEqualTo(1);
        assertThat(wishlistCount(member)).isEqualTo(1);
    }

    @Test
    @DisplayName("이전 AI 계약 응답은 fallback 없이 그 배치를 실패시킨다")
    void doesNotFallbackToLegacyContract() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.failAnalysisForVideo(
                "video-a",
                200,
                "{\"message\":\"ok\",\"data\":{\"results\":[{\"video_url\":\"https://youtube.com/shorts/video-a\","
                        + "\"status\":\"success\",\"error\":null,\"place_name\":\"구 계약\",\"address\":\"주소\","
                        + "\"category\":\"장소\",\"lat\":37.5,\"lng\":127.0}]}}");
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(analysisStatus("video-a")).isEqualTo("FAILED");
        assertThat(failureCode("video-a")).isEqualTo("INVALID_AI_RESPONSE");
        assertThat(guideCount()).isZero();
        assertThat(wishlistCount(member)).isZero();
    }

    @Test
    @DisplayName("동기화 중에는 임베딩을 호출하지 않고 신규 가이드마다 아웃박스 행이 생긴다")
    void recordsOutboxInsteadOfEmbeddingDuringSync() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        assertThat(data(events.getLast()).get("video_failed_count").asInt()).isZero();
        assertThat(AI_STUB.embeddingRequestCount()).isZero();
        assertThat(guideCount()).isEqualTo(2);
        assertThat(outboxCount()).isEqualTo(2);
        assertThat(pendingOutboxCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("임베딩 실패는 동기화 결과에 영향을 주지 않고 아웃박스에 남는다")
    void leavesEmbeddingRetryToOutbox() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        AI_STUB.failEmbedding(500, "{\"message\":\"failure\"}");
        Member member = newMember();

        List<SseEvent> events = runToSuccess(token(member));

        assertThat(data(events.getLast()).get("video_failed_count").asInt()).isZero();
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(2);
        assertThat(AI_STUB.embeddingRequestCount()).isZero();
        assertThat(pendingOutboxCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("AI 응답이 먼저 온 배치부터 커밋한다")
    void commitsBatchesInCompletionOrder() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 20)));
        AI_STUB.setAnalysisDelayForVideo("v0", Duration.ofMillis(800));
        Member member = newMember();

        runToSuccess(token(member));

        assertThat(analysisCompletedAt("v10")).isBefore(analysisCompletedAt("v0"));
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
    @DisplayName("여러 회원이 동시에 동기화해도 동시 분석 요청은 3건을 넘지 않는다")
    void concurrentSyncsShareGlobalAnalysisLimit() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 30)));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        List<Member> members = List.of(newMember(), newMember(), newMember());

        try (var executor = Executors.newFixedThreadPool(members.size())) {
            List<Future<List<SseEvent>>> runs = new ArrayList<>();
            for (Member member : members) {
                runs.add(executor.submit(() -> runToSuccess(token(member))));
            }
            for (Future<List<SseEvent>> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(AI_STUB.analysisRequestCount()).isEqualTo(9);
        assertThat(AI_STUB.maxConcurrentRequests()).isEqualTo(3);
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

    private String analysisStatus(String videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM video_analysis WHERE video_id = ? ORDER BY id DESC LIMIT 1", String.class, videoId);
    }

    private String failureCode(String videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_code FROM video_analysis WHERE video_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                videoId);
    }

    private JsonNode excludedCandidates(String videoId) throws Exception {
        return objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT excluded_candidates FROM video_analysis WHERE video_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                videoId));
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outing_guide_embedding_outbox", Integer.class);
    }

    private int pendingOutboxCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_guide_embedding_outbox WHERE status = 'PENDING'", Integer.class);
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

    private Timestamp analysisCompletedAt(String videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT completed_at FROM video_analysis WHERE video_id = ?", Timestamp.class, videoId);
    }

    /// DB 유일성 제약은 별도 이관 티켓이 적용한다. 이 테스트는 같은 제약을 임시로 걸어 충돌을 재현한다.
    private void createGuideIdentityIndex() {
        jdbcTemplate.execute("ALTER TABLE outing_guide ADD UNIQUE KEY uk_guide_identity (type, name, address)");
    }

    private static OutingPlace place(String name, String address) {
        return new OutingPlace("카페", name, "설명", "상세", address, new Coordinate(37.5f, 127.0f), null);
    }

    private static OutingEvent event(String name, String address) {
        return new OutingEvent("전시", name, "설명", "상세", address, new Coordinate(37.5f, 127.0f), null, null, null);
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

                    // 두 배치 트랜잭션이 가이드를 저장한 뒤 동시에 커밋하도록 맞춘다.
                    CountDownLatch barrier = WISHLIST_BARRIER.get();
                    if (barrier != null) {
                        barrier.countDown();
                        try {
                            if (!barrier.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("테스트용 위시리스트 배리어 대기 시간 초과");
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("테스트용 위시리스트 배리어 대기가 중단되었습니다", error);
                        }
                    }

                    super.sync(memberId, analyzedGuides);
                }
            };
        }
    }
}
