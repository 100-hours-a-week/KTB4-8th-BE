package eightjbbm.keepgo.integration;

import static eightjbbm.keepgo.integration.stub.YoutubeStub.video;
import static eightjbbm.keepgo.integration.stub.YoutubeStub.videos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.integration.sse.SseEvent;
import eightjbbm.keepgo.integration.sse.SseOpenException;
import eightjbbm.keepgo.integration.sse.SseTestClient;
import eightjbbm.keepgo.integration.stub.AiServerStub;
import eightjbbm.keepgo.integration.stub.YoutubeStub;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubesync.YoutubeConnectionChecker;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistService;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import eightjbbm.keepgo.util.exception.YoutubeNotConnectedException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(YoutubeSyncIntegrationTest.StubConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
        properties = {
            "keepgo.youtube-sync.poll-interval-ms=50",
            "keepgo.youtube-sync.max-concurrent-syncs=1",
            "keepgo.youtube-sync.recent-result-window=2s",
            "keepgo.youtube-sync.fetch-weight=10",
            "keepgo.embedding-outbox.poll-interval=3600000"
        })
@DisplayName("YouTube SSE 동기화 통합 테스트")
class YoutubeSyncIntegrationTest extends MySqlIntegrationTestSupport {

    private static final YoutubeStub YOUTUBE = new YoutubeStub();
    private static final long RECENT_RESULT_WINDOW_MILLIS = 2_200;

    private final int port;
    private final AccessTokenManager accessTokenManager;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutingWishlistService outingWishlistService;

    YoutubeSyncIntegrationTest(
            @LocalServerPort int port,
            AccessTokenManager accessTokenManager,
            MemberRepository memberRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OutingWishlistService outingWishlistService) {
        this.port = port;
        this.accessTokenManager = accessTokenManager;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.outingWishlistService = outingWishlistService;
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
    @DisplayName("YouTube 조회가 끝나기 전에 accepted를 받고 progress를 거쳐 success를 받는다")
    void streamsAcceptedProgressAndSuccess() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b")).addPage(videos("video-c"));
        YOUTUBE.setDelay(Duration.ofMillis(200));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        Member member = newMember();

        List<SseEvent> events = new ArrayList<>();

        try (SseTestClient client = open(token(member))) {
            SseEvent accepted = client.next(Duration.ofSeconds(5));
            events.add(accepted);

            assertThat(accepted.event()).isEqualTo("youtube-sync.accepted");
            // 아직 YouTube 조회가 끝나지 않아 분석 대상 수를 모른다.
            assertThat(data(accepted).get("video_target_count").isNull()).isTrue();
            assertThat(data(accepted).get("progress").asInt()).isZero();

            events.addAll(client.collectUntilClosed(Duration.ofSeconds(15)));
        }

        assertThat(events)
                .extracting(SseEvent::event)
                .contains("youtube-sync.progress")
                .endsWith("youtube-sync.success");
        assertThat(eventIds(events)).isSortedAccordingTo(Long::compareTo);

        JsonNode success = data(events.get(events.size() - 1));
        assertThat(success.get("video_target_count").asInt()).isEqualTo(3);
        assertThat(success.get("video_fetched_count").asInt()).isEqualTo(3);
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(3);
        assertThat(success.get("video_failed_count").asInt()).isZero();
        assertThat(success.get("progress").asInt()).isEqualTo(100);

        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(3);
    }

    @Test
    @DisplayName("분석 대상 수는 필터를 통과한 중복 없는 영상 수이고 진척률은 0에서 100으로 단조 증가한다")
    void progressIncreasesMonotonically() throws Exception {
        List<Video> firstPage = new ArrayList<>(videos(ids(0, 15)));
        List<Video> secondPage = new ArrayList<>(videos(ids(15, 25)));
        secondPage.add(privateVideo("video-private"));
        secondPage.add(video("v0"));
        YOUTUBE.addPage(firstPage).addPage(secondPage);
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        Member member = newMember();

        List<SseEvent> events = new ArrayList<>();

        try (SseTestClient client = open(token(member))) {
            events.add(client.next(Duration.ofSeconds(5)));
            events.addAll(client.collectUntilClosed(Duration.ofSeconds(20)));
        }

        JsonNode success = data(events.get(events.size() - 1));
        assertThat(success.get("video_fetched_count").asInt()).isEqualTo(27);
        assertThat(success.get("video_target_count").asInt()).isEqualTo(25);
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(25);

        List<Integer> progresses = events.stream()
                .map(event -> data(event).get("progress").asInt())
                .toList();
        assertThat(progresses).isSorted();
        assertThat(progresses.get(0)).isZero();
        assertThat(progresses.get(progresses.size() - 1)).isEqualTo(100);
        assertThat(progresses).anyMatch(progress -> progress > 0 && progress < 100);

        assertThat(eventIds(events)).isSortedAccordingTo(Long::compareTo);
        assertThat(syncStatus(member)).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("동기화 중 다시 요청하면 새 동기화 없이 현재 상태 accepted로 붙는다")
    void duplicateRequestAttachesToRunningSync() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 12)));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(800));
        Member member = newMember();
        String token = token(member);

        try (SseTestClient first = open(token)) {
            assertThat(first.next(Duration.ofSeconds(5)).event()).isEqualTo("youtube-sync.accepted");
            SseEvent progress = first.next(Duration.ofSeconds(5));
            assertThat(progress.event()).isEqualTo("youtube-sync.progress");
            int progressSoFar = data(progress).get("progress").asInt();

            try (SseTestClient second = open(token)) {
                SseEvent accepted = second.next(Duration.ofSeconds(5));
                assertThat(accepted.event()).isEqualTo("youtube-sync.accepted");
                assertThat(data(accepted).get("video_target_count").asInt()).isEqualTo(12);
                assertThat(data(accepted).get("progress").asInt()).isGreaterThanOrEqualTo(progressSoFar);

                assertThat(second.collectUntilClosed(Duration.ofSeconds(20)))
                        .extracting(SseEvent::event)
                        .endsWith("youtube-sync.success");
            }

            assertThat(first.collectUntilClosed(Duration.ofSeconds(20)))
                    .extracting(SseEvent::event)
                    .endsWith("youtube-sync.success");
        }

        assertThat(syncCount(member)).isEqualTo(1);
        assertThat(wishlistCount(member)).isEqualTo(12);
    }

    @Test
    @DisplayName("종료 후 최근 결과 시간 안에는 종료 이벤트를 받고, 지나면 새 동기화를 시작한다")
    void reconnectsToRecentResultWithinWindow() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        Member member = newMember();
        String token = token(member);

        runToSuccess(token);
        assertThat(syncCount(member)).isEqualTo(1);

        ReconnectResult recent = reconnect(token);
        assertThat(recent.events())
                .extracting(SseEvent::event)
                .containsExactly("youtube-sync.accepted", "youtube-sync.success");
        assertThat(syncCount(member)).isEqualTo(1);

        Thread.sleep(RECENT_RESULT_WINDOW_MILLIS);

        ReconnectResult renewed = reconnect(token);
        assertThat(renewed.events().get(0).event()).isEqualTo("youtube-sync.accepted");
        assertThat(data(renewed.events().get(0)).get("video_target_count").isNull())
                .isTrue();
        assertThat(renewed.events()).extracting(SseEvent::event).endsWith("youtube-sync.success");
        assertThat(syncCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제한 가이드는 다시 담지 않고, 새 가이드만 추가하며 삭제 이력은 보존한다")
    void doesNotReAddDeletedGuides() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b"));
        Member member = newMember();
        String token = token(member);

        runToSuccess(token);
        assertThat(wishlistCount(member)).isEqualTo(2);
        Long deletedEntryId = jdbcTemplate.queryForObject("""
                SELECT ocp.id
                FROM outing_collection_private ocp
                JOIN outing_guide og ON og.id = ocp.guide_id
                WHERE ocp.member_id = ? AND og.name = ? AND ocp.deleted_at IS NULL
                """, Long.class, member.getId(), "스텁 가이드 video-a");
        outingWishlistService.removePlace(member.getId(), deletedEntryId);
        assertThat(deletedWishlistCount(member)).isEqualTo(1);

        // 같은 영상과 새 영상을 함께 동기화한다.
        YOUTUBE.reset();
        YOUTUBE.addPage(videos("video-a", "video-b", "video-c"));
        Thread.sleep(RECENT_RESULT_WINDOW_MILLIS);
        List<SseEvent> events = runToSuccess(token);

        assertThat(data(events.get(events.size() - 1))
                        .get("video_analyzed_count")
                        .asInt())
                .isEqualTo(3);
        assertThat(wishlistCount(member)).isEqualTo(2);
        assertThat(deletedWishlistCount(member)).isEqualTo(1);
        assertThat(wishlistHistoryCount(member)).isEqualTo(3);
        assertThat(syncCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("복수 가이드 중 하나를 삭제한 뒤 재동기화해도 나머지와 삭제 이력이 보존된다")
    void preservesDeletedEntryAmongMultipleGuidesOnResync() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        AI_STUB.useGuidesForVideo(
                "video-a",
                new AiServerStub.GuideTemplate("삭제할 장소", "서울특별시 종로구 다중로 1", "장소"),
                new AiServerStub.GuideTemplate("남길 행사", "서울특별시 종로구 다중로 2", "이벤트"));
        Member member = newMember();
        String token = token(member);
        runToSuccess(token);
        Long deletedEntryId = jdbcTemplate.queryForObject("""
                SELECT w.id FROM outing_collection_private w
                JOIN outing_guide g ON g.id = w.guide_id
                WHERE w.member_id = ? AND g.name = '삭제할 장소' AND w.deleted_at IS NULL
                """, Long.class, member.getId());
        outingWishlistService.removePlace(member.getId(), deletedEntryId);
        var history = jdbcTemplate.queryForList("""
                SELECT id, guide_id, deleted_at FROM outing_collection_private WHERE member_id = ? ORDER BY id
                """, member.getId());

        YOUTUBE.reset();
        YOUTUBE.addPage(videos("video-a", "video-b"));
        jdbcTemplate.update("""
                UPDATE youtube_sync SET finished_at = TIMESTAMPADD(SECOND, -10, UTC_TIMESTAMP(6))
                WHERE member_id = ? AND status = 'SUCCEEDED'
                """, member.getId());
        List<SseEvent> events = runToSuccess(token);

        JsonNode success = data(events.getLast());
        assertThat(success.get("video_target_count").asInt()).isEqualTo(2);
        assertThat(success.get("video_analyzed_count").asInt()).isEqualTo(2);
        assertThat(success.get("video_failed_count").asInt()).isZero();
        assertThat(jdbcTemplate.queryForList("""
                SELECT id, guide_id, deleted_at FROM outing_collection_private WHERE member_id = ? ORDER BY id
                """, member.getId())).containsAll(history);
        assertThat(wishlistCount(member)).isEqualTo(2);
        assertThat(deletedWishlistCount(member)).isEqualTo(1);
        assertThat(wishlistHistoryCount(member)).isEqualTo(3);
        assertThat(syncCount(member)).isEqualTo(2);
    }

    @Test
    @DisplayName("클라이언트 연결이 끊겨도 동기화는 끝까지 실행된다")
    void syncContinuesAfterClientDisconnects() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b")).addPage(videos("video-c"));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(300));
        Member member = newMember();

        try (SseTestClient client = open(token(member))) {
            assertThat(client.next(Duration.ofSeconds(5)).event()).isEqualTo("youtube-sync.accepted");
        }

        awaitStatus(member, "SUCCEEDED");
        assertThat(wishlistCount(member)).isEqualTo(3);
    }

    @Test
    @DisplayName("인스턴스 동시 실행 상한을 넘으면 SYNC_BUSY 오류 이벤트를 받는다")
    void rejectsWhenInstanceIsBusy() throws Exception {
        YOUTUBE.addPage(videos(ids(0, 12)));
        AI_STUB.setAnalysisDelay(Duration.ofMillis(800));
        Member busyMember = newMember();
        Member waitingMember = newMember();

        try (SseTestClient busy = open(token(busyMember))) {
            assertThat(busy.next(Duration.ofSeconds(5)).event()).isEqualTo("youtube-sync.accepted");

            try (SseTestClient rejected = open(token(waitingMember))) {
                SseEvent error = rejected.next(Duration.ofSeconds(5));
                assertThat(error.event()).isEqualTo("youtube-sync.service-unavailable");

                JsonNode payload = data(error);
                assertThat(payload.get("code").asText()).isEqualTo("SYNC_BUSY");
                assertThat(payload.get("trace_id").asText()).isNotBlank();
                assertThat(payload.get("type").asText()).isEqualTo("about:blank");
                assertThat(payload.get("title").asText()).isEqualTo("Service Unavailable");
                assertThat(payload.get("instance").asText()).isNotBlank();
                rejected.awaitClosed(Duration.ofSeconds(5));
            }

            assertThat(syncCount(waitingMember)).isZero();
            busy.collectUntilClosed(Duration.ofSeconds(20));
        }
    }

    @Test
    @DisplayName("인증 없이 스트림을 열면 401을 받는다")
    void rejectsUnauthenticatedRequest() {
        assertThatThrownBy(() -> SseTestClient.open(syncUri()))
                .isInstanceOfSatisfying(
                        SseOpenException.class,
                        error -> assertThat(error.status()).isEqualTo(401));
    }

    @Test
    @DisplayName("YouTube 연동이 없으면 스트림을 열기 전에 409 YOUTUBE_NOT_CONNECTED를 받는다")
    void rejectsWhenYoutubeNotConnected() {
        YOUTUBE.expireToken();
        Member member = newMember();

        assertThatThrownBy(() -> open(token(member))).isInstanceOfSatisfying(SseOpenException.class, error -> {
            assertThat(error.status()).isEqualTo(409);
            assertThat(error.body()).contains("YOUTUBE_NOT_CONNECTED");
        });
        assertThat(syncCount(member)).isZero();
    }

    @Test
    @DisplayName("조회 중 토큰이 만료되면 conflict 오류 이벤트를 보낸다")
    void emitsConflictWhenTokenExpiresDuringFetch() throws Exception {
        YOUTUBE.addPage(videos("video-a", "video-b")).addPage(videos("video-c"));
        YOUTUBE.expireTokenAfter(1);
        Member member = newMember();

        List<SseEvent> events = new ArrayList<>();
        try (SseTestClient client = open(token(member))) {
            events.add(client.next(Duration.ofSeconds(5)));
            events.addAll(client.collectUntilClosed(Duration.ofSeconds(10)));
        }

        assertThat(events).extracting(SseEvent::event).endsWith("youtube-sync.conflict");
        JsonNode payload = data(events.get(events.size() - 1));
        assertThat(payload.get("code").asText()).isEqualTo("YOUTUBE_REAUTHORIZATION_REQUIRED");
        assertThat(payload.get("trace_id").asText()).isNotBlank();
        assertThat(payload.get("type").asText()).isEqualTo("about:blank");
        assertThat(payload.get("title").asText()).isEqualTo("Conflict");
        assertThat(payload.get("instance").asText()).isNotBlank();

        assertThat(syncStatus(member)).isEqualTo("FAILED");
        assertThat(wishlistCount(member)).isZero();
    }

    @Test
    @DisplayName("일반 YouTube 조회 오류는 bad-gateway 오류 이벤트를 보낸다")
    void emitsBadGatewayWhenYoutubeFetchFails() throws Exception {
        YOUTUBE.addPage(videos("video-a"));
        YOUTUBE.failWith(new ServiceUnavailableException());
        Member member = newMember();

        List<SseEvent> events = new ArrayList<>();
        try (SseTestClient client = open(token(member))) {
            events.add(client.next(Duration.ofSeconds(5)));
            events.addAll(client.collectUntilClosed(Duration.ofSeconds(10)));
        }

        assertThat(events).extracting(SseEvent::event).endsWith("youtube-sync.bad-gateway");
        JsonNode payload = data(events.get(events.size() - 1));
        assertThat(payload.get("code").asText()).isEqualTo("YOUTUBE_FETCH_FAILED");
        assertThat(syncStatus(member)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("plain 동기화 엔드포인트는 제거됐다")
    void plainEndpointIsRemoved() throws Exception {
        Member member = newMember();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync-plain"))
                .header("Authorization", "Bearer " + token(member))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private SseTestClient open(String token) throws Exception {
        return SseTestClient.open(syncUri(), token);
    }

    private URI syncUri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v3/member/youtube-sync");
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

    private ReconnectResult reconnect(String token) throws Exception {
        List<SseEvent> events = new ArrayList<>();
        try (SseTestClient client = open(token)) {
            SseEvent first = client.next(Duration.ofSeconds(5));
            events.add(first);
            events.addAll(client.collectUntilClosed(Duration.ofSeconds(20)));
            client.awaitClosed(Duration.ofSeconds(5));
        }
        return new ReconnectResult(events);
    }

    private Member newMember() {
        return memberRepository.save(Member.create("SSE 동기화 회원"));
    }

    private String token(Member member) {
        return accessTokenManager.issue(member.getId(), List.of("ROLE_USER"));
    }

    private JsonNode data(SseEvent event) {
        return objectMapper.readTree(event.data());
    }

    private List<Long> eventIds(List<SseEvent> events) {
        return events.stream().map(event -> Long.valueOf(event.id())).toList();
    }

    private String syncStatus(Member member) {
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

    private int deletedWishlistCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                member.getId());
    }

    private int wishlistHistoryCount(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outing_collection_private WHERE member_id = ?", Integer.class, member.getId());
    }

    private void awaitStatus(Member member, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        String status = null;
        while (System.nanoTime() < deadline) {
            List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM youtube_sync WHERE member_id = ? ORDER BY id DESC",
                    String.class,
                    member.getId());
            status = statuses.isEmpty() ? null : statuses.get(0);
            if (expected.equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("동기화 상태가 " + expected + "가 되지 않았습니다: " + status);
    }

    private static String[] ids(int from, int to) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < to; i++) {
            ids.add("v" + i);
        }
        return ids.toArray(String[]::new);
    }

    private static Video privateVideo(String id) {
        Video video = video(id);
        video.getStatus().setPrivacyStatus("private");
        return video;
    }

    private record ReconnectResult(List<SseEvent> events) {}

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
    }
}
