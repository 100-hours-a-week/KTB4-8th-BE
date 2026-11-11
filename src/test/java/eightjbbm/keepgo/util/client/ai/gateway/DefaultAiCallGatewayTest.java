package eightjbbm.keepgo.util.client.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisJobClient;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AI 호출 게이트웨이")
class DefaultAiCallGatewayTest {

    private static final AiCallProperties PROPERTIES = new AiCallProperties(
            3,
            1,
            Duration.ofSeconds(150),
            Duration.ofMillis(20),
            Duration.ofSeconds(130),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofSeconds(30));

    private final AiServerService ai = mock(AiServerService.class);
    private final VideoAnalysisJobClient jobClient = mock(VideoAnalysisJobClient.class);
    private final AiCallSlotRepository slots = mock(AiCallSlotRepository.class);
    private final AtomicInteger slotHeld = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private DefaultAiCallGateway gateway;

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.shutdown();
        }
    }

    @Test
    @DisplayName("전역 슬롯 상한을 넘겨 호출하지 않는다")
    void respectsGlobalSlotLimit() {
        int limit = 2;
        givenSlots(limit);
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class))).thenAnswer(call -> {
            int active = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(100);
            } finally {
                inFlight.decrementAndGet();
            }
            return failure();
        });
        gateway = startedGateway();

        List<CompletableFuture<VideoAnalysisResponse>> calls = List.of(
                gateway.submitVideoAnalysis(1, List.of("a"), Map.of(), null),
                gateway.submitVideoAnalysis(2, List.of("b"), Map.of(), null),
                gateway.submitVideoAnalysis(3, List.of("c"), Map.of(), null),
                gateway.submitVideoAnalysis(4, List.of("d"), Map.of(), null));

        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

        assertThat(maxInFlight.get()).isEqualTo(limit);
    }

    @Test
    @DisplayName("한 동기화가 배치를 여러 개 제출해도 다른 동기화 차례를 먼저 준다")
    void roundRobinsBetweenSyncs() throws Exception {
        givenSlots(1);
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class))).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            order.add(ids.getFirst());
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return failure();
        });
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> first = gateway.submitVideoAnalysis(1, List.of("a1"), Map.of(), null);
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<VideoAnalysisResponse> second = gateway.submitVideoAnalysis(1, List.of("a2"), Map.of(), null);
        CompletableFuture<VideoAnalysisResponse> other = gateway.submitVideoAnalysis(2, List.of("b1"), Map.of(), null);
        releaseFirst.countDown();

        CompletableFuture.allOf(first, second, other).join();

        assertThat(order).containsExactly("a1", "b1", "a2");
    }

    @Test
    @DisplayName("대기열에 여러 건이 쌓여도 동기화마다 번갈아 차례를 준다")
    void rotatesBetweenSyncsWithBacklog() throws Exception {
        givenSlots(1);
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class))).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            order.add(ids.getFirst());
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return failure();
        });
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> first = gateway.submitVideoAnalysis(1, List.of("a1"), Map.of(), null);
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        List<CompletableFuture<VideoAnalysisResponse>> queued = List.of(
                first,
                gateway.submitVideoAnalysis(1, List.of("a2"), Map.of(), null),
                gateway.submitVideoAnalysis(1, List.of("a3"), Map.of(), null),
                gateway.submitVideoAnalysis(2, List.of("b1"), Map.of(), null),
                gateway.submitVideoAnalysis(2, List.of("b2"), Map.of(), null));
        releaseFirst.countDown();

        CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).join();

        assertThat(order).containsExactly("a1", "b1", "a2", "b2", "a3");
    }

    @Test
    @DisplayName("슬롯을 얻은 뒤 전송 직전에만 분석 중을 기록한다")
    void recordsInProgressJustBeforeSend() throws Exception {
        givenSlots(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger beforeSendCalls = new AtomicInteger();
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class))).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return failure();
        });
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> first =
                gateway.submitVideoAnalysis(1, List.of("a1"), Map.of(), ids -> {
                    beforeSendCalls.incrementAndGet();
                    return ids;
                });
        CompletableFuture<VideoAnalysisResponse> second =
                gateway.submitVideoAnalysis(2, List.of("b1"), Map.of(), ids -> {
                    beforeSendCalls.incrementAndGet();
                    return ids;
                });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(beforeSendCalls.get()).isEqualTo(1);
        releaseFirst.countDown();
        CompletableFuture.allOf(first, second).join();

        assertThat(beforeSendCalls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("종료하면 아직 보내지 않은 호출을 실패로 끝낸다")
    void failsPendingCallsOnShutdown() {
        when(slots.acquire(any(AiCallType.class), any(Duration.class))).thenReturn(Optional.empty());
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> pending = gateway.submitVideoAnalysis(1, List.of("a"), Map.of(), null);
        gateway.shutdown();

        assertThat(pending).failsWithin(Duration.ofSeconds(1));
        assertThat(gateway.submitVideoAnalysis(2, List.of("b"), Map.of(), null)).isCompletedExceptionally();
    }

    @Test
    @DisplayName("전체 제한을 넘긴 호출은 실패로 끝내고 전송을 인터럽트한 뒤 슬롯을 반납한다")
    void failsCallExceedingTotalTimeout() throws Exception {
        givenSlots(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class))).thenAnswer(call -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException error) {
                interrupted.countDown();
            }
            return failure();
        });
        gateway = startedGateway(new AiCallProperties(
                1,
                1,
                Duration.ofSeconds(150),
                Duration.ofMillis(20),
                Duration.ofMillis(200),
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                Duration.ofMillis(100)));

        CompletableFuture<VideoAnalysisResponse> call = gateway.submitVideoAnalysis(1, List.of("a"), Map.of(), null);

        assertThat(call)
                .failsWithin(Duration.ofSeconds(2))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(slots, timeout(2_000)).release(any(AiCallType.class), anyString());
    }

    @Test
    @DisplayName("임베딩은 장소마다 AI 호출 하나를 보낸다")
    void embedsEachGuideWithOwnCall() {
        givenSlots(1);
        List<OutingGuide> guides = List.of(guide(1), guide(2), guide(3));
        gateway = startedGateway();

        gateway.submitEmbedding(guides).join();

        verify(ai, times(3)).embedPlace(any(OutingGuide.class));
        guides.forEach(guide -> verify(ai).embedPlace(guide));
    }

    @Test
    @DisplayName("임베딩 중 한 장소가 실패해도 나머지는 보내고 전체는 실패로 끝낸다")
    void embedsRemainingGuidesWhenOneFails() {
        givenSlots(1);
        List<OutingGuide> guides = List.of(guide(1), guide(2), guide(3));
        doThrow(new IllegalStateException("AI down")).when(ai).embedPlace(guides.get(1));
        gateway = startedGateway();

        CompletableFuture<Void> call = gateway.submitEmbedding(guides);

        assertThat(call)
                .failsWithin(Duration.ofSeconds(2))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class);
        verify(ai).embedPlace(guides.get(0));
        verify(ai).embedPlace(guides.get(2));
    }

    @Test
    @DisplayName("임대가 전체 제한보다 짧은 설정은 거부한다")
    void rejectsLeaseShorterThanTimeout() {
        assertThatThrownBy(() -> new AiCallProperties(
                        3,
                        1,
                        Duration.ofSeconds(30),
                        Duration.ofMillis(200),
                        Duration.ofSeconds(130),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("beforeSend가 없으면 요청한 영상을 모두 보낸다")
    void sendsAllRequestedVideosWithoutBeforeSend() {
        givenSlots(1);
        when(jobClient.analyzeVideo(anyList(), anyMap(), any(Duration.class)))
                .thenReturn(new VideoAnalysisResponse(List.of()));
        gateway = startedGateway();

        gateway.submitVideoAnalysis(1, List.of("a", "b"), Map.of(), null).join();

        verify(jobClient).analyzeVideo(eq(List.of("a", "b")), eq(Map.of()), any(Duration.class));
    }

    @Test
    @DisplayName("beforeSend가 요청하지 않은 영상을 돌려주면 보내지 않고 실패로 끝낸다")
    void failsWhenBeforeSendReturnsUnrequestedVideo() {
        givenSlots(1);
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> call =
                gateway.submitVideoAnalysis(1, List.of("a"), Map.of(), requested -> List.of("a", "other"));

        assertThat(call)
                .failsWithin(Duration.ofSeconds(2))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class);
        verify(jobClient, never()).analyzeVideo(anyList(), anyMap(), any(Duration.class));
    }

    private static OutingGuide guide(long id) {
        OutingGuide guide = mock(OutingGuide.class);
        when(guide.getId()).thenReturn(id);
        return guide;
    }

    private DefaultAiCallGateway startedGateway() {
        return startedGateway(PROPERTIES);
    }

    private DefaultAiCallGateway startedGateway(AiCallProperties properties) {
        DefaultAiCallGateway started = new DefaultAiCallGateway(ai, jobClient, slots, properties);
        started.start();
        return started;
    }

    private void givenSlots(int limit) {
        when(slots.acquire(any(AiCallType.class), any(Duration.class))).thenAnswer(call -> {
            while (true) {
                int current = slotHeld.get();
                if (current >= limit) {
                    return Optional.empty();
                }
                if (slotHeld.compareAndSet(current, current + 1)) {
                    return Optional.of("holder-" + current);
                }
            }
        });
        doAnswer(call -> {
                    slotHeld.decrementAndGet();
                    return null;
                })
                .when(slots)
                .release(any(AiCallType.class), anyString());
    }

    private static VideoAnalysisResponse failure() {
        return new VideoAnalysisResponse(List.of());
    }
}
