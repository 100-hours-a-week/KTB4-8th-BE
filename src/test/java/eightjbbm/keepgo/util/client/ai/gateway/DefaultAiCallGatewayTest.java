package eightjbbm.keepgo.util.client.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisJobClient;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.time.Duration;
import java.util.List;
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
        when(ai.analyzeVideoV2(anyList())).thenAnswer(call -> {
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

        List<CompletableFuture<AnalyzeVideoResponseV2>> calls = List.of(
                gateway.submitAnalysis(1, List.of("a"), () -> {}),
                gateway.submitAnalysis(2, List.of("b"), () -> {}),
                gateway.submitAnalysis(3, List.of("c"), () -> {}),
                gateway.submitAnalysis(4, List.of("d"), () -> {}));

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
        when(ai.analyzeVideoV2(anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            order.add(ids.getFirst());
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return failure();
        });
        gateway = startedGateway();

        CompletableFuture<AnalyzeVideoResponseV2> first = gateway.submitAnalysis(1, List.of("a1"), () -> {});
        CompletableFuture<AnalyzeVideoResponseV2> second = gateway.submitAnalysis(1, List.of("a2"), () -> {});
        CompletableFuture<AnalyzeVideoResponseV2> other = gateway.submitAnalysis(2, List.of("b1"), () -> {});
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        releaseFirst.countDown();

        CompletableFuture.allOf(first, second, other).join();

        assertThat(order).containsExactly("a1", "b1", "a2");
    }

    @Test
    @DisplayName("슬롯을 얻은 뒤 전송 직전에만 분석 중을 기록한다")
    void recordsInProgressJustBeforeSend() throws Exception {
        givenSlots(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger beforeSendCalls = new AtomicInteger();
        when(ai.analyzeVideoV2(anyList())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return failure();
        });
        gateway = startedGateway();

        CompletableFuture<AnalyzeVideoResponseV2> first =
                gateway.submitAnalysis(1, List.of("a1"), beforeSendCalls::incrementAndGet);
        CompletableFuture<AnalyzeVideoResponseV2> second =
                gateway.submitAnalysis(2, List.of("b1"), beforeSendCalls::incrementAndGet);
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

        CompletableFuture<AnalyzeVideoResponseV2> pending = gateway.submitAnalysis(1, List.of("a"), () -> {});
        gateway.shutdown();

        assertThat(pending).failsWithin(Duration.ofSeconds(1));
        assertThat(gateway.submitAnalysis(2, List.of("b"), () -> {})).isCompletedExceptionally();
    }

    @Test
    @DisplayName("전체 제한을 넘긴 호출은 실패로 끝내고 전송을 인터럽트한 뒤 슬롯을 반납한다")
    void failsCallExceedingTotalTimeout() throws Exception {
        givenSlots(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(ai.analyzeVideoV2(anyList())).thenAnswer(call -> {
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
                Duration.ofSeconds(30)));

        CompletableFuture<AnalyzeVideoResponseV2> call = gateway.submitAnalysis(1, List.of("a"), () -> {});

        assertThat(call)
                .failsWithin(Duration.ofSeconds(2))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(slots, timeout(2_000)).release(any(AiCallType.class), anyString());
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
                        Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("beforeSend가 없으면 요청한 영상을 모두 보낸다")
    void sendsAllRequestedVideosWithoutBeforeSend() {
        givenSlots(1);
        when(jobClient.analyzeVideo(anyList(), any(Duration.class))).thenReturn(new VideoAnalysisResponse(List.of()));
        gateway = startedGateway();

        gateway.submitVideoAnalysis(1, List.of("a", "b"), null).join();

        verify(jobClient).analyzeVideo(eq(List.of("a", "b")), any(Duration.class));
    }

    @Test
    @DisplayName("beforeSend가 요청하지 않은 영상을 돌려주면 보내지 않고 실패로 끝낸다")
    void failsWhenBeforeSendReturnsUnrequestedVideo() {
        givenSlots(1);
        gateway = startedGateway();

        CompletableFuture<VideoAnalysisResponse> call =
                gateway.submitVideoAnalysis(1, List.of("a"), requested -> List.of("a", "other"));

        assertThat(call)
                .failsWithin(Duration.ofSeconds(2))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class);
        verify(jobClient, never()).analyzeVideo(anyList(), any(Duration.class));
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

    private static AnalyzeVideoResponseV2 failure() {
        return new AnalyzeVideoResponseV2.Failure("failure", new AnalyzeVideoResponseV2.Failure.Data());
    }
}
