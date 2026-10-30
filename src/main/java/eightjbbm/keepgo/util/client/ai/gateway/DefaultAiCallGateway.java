package eightjbbm.keepgo.util.client.ai.gateway;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import jakarta.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// DB 슬롯으로 전역 동시 호출을 제한하고, 인스턴스 안에서는 동기화별 대기열을 라운드 로빈으로 꺼낸다.
///
/// 호출자는 배치를 제출만 하고 결과 Future를 받는다. 슬롯을 얻지 못하면 200ms 간격으로 다시
/// 시도하며, 대기 시간은 HTTP 타임아웃에 포함되지 않는다. 슬롯은 실제 전송이 끝나면 반납한다.
@Slf4j
@Service
public class DefaultAiCallGateway implements AiCallGateway {

    private final AiServerService aiServerService;
    private final AiCallSlotRepository slots;
    private final AiCallProperties properties;

    private final Object lock = new Object();
    private final Map<String, Deque<QueuedCall<?>>> queues = new LinkedHashMap<>();
    private int cursor;

    private final ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-call-gateway");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultAiCallGateway(
            AiServerService aiServerService, AiCallSlotRepository slots, AiCallProperties properties) {
        this.aiServerService = aiServerService;
        this.slots = slots;
        this.properties = properties;

        long intervalMillis = Math.max(1L, properties.retryInterval().toMillis());
        dispatcher.scheduleWithFixedDelay(this::dispatchSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletableFuture<AnalyzeVideoResponseV2> submitAnalysis(
            long syncId, List<String> videoIds, Runnable beforeSend) {
        List<String> ids = List.copyOf(videoIds);
        Runnable start = beforeSend == null ? () -> {} : beforeSend;
        return enqueue("sync:" + syncId, AiCallType.ANALYSIS, () -> aiServerService.analyzeVideoV2(ids), start);
    }

    @Override
    public CompletableFuture<Void> submitEmbedding(List<OutingGuide> guides) {
        List<OutingGuide> copy = List.copyOf(guides);
        return enqueue(
                "embedding",
                AiCallType.EMBEDDING,
                () -> {
                    aiServerService.embedPlaces(copy);
                    return null;
                },
                () -> {});
    }

    private <T> CompletableFuture<T> enqueue(String queueKey, AiCallType type, Supplier<T> call, Runnable beforeSend) {
        CompletableFuture<T> future = new CompletableFuture<>();
        synchronized (lock) {
            boolean newQueue = !queues.containsKey(queueKey);
            queues.computeIfAbsent(queueKey, key -> new ArrayDeque<>())
                    .addLast(new QueuedCall<>(type, call, beforeSend, future));
            if (newQueue) {
                // 새로 참여한 동기화가 이미 쌓인 대기열 뒤에서 굶지 않게 다음 차례를 준다.
                cursor = new ArrayList<>(queues.keySet()).indexOf(queueKey);
            }
        }
        dispatcher.execute(this::dispatchSafely);
        return future;
    }

    private void dispatchSafely() {
        try {
            dispatch();
        } catch (RuntimeException error) {
            log.error("AI 호출 게이트웨이 대기열 처리 실패", error);
        }
    }

    /// 슬롯을 얻은 호출 하나를 보낸 뒤 다음 대기열부터 다시 본다. 슬롯이 없으면 다음 주기에 재시도한다.
    private void dispatch() {
        while (true) {
            QueuedCall<?> call = null;
            String holder = null;
            synchronized (lock) {
                List<String> keys = new ArrayList<>(queues.keySet());
                if (keys.isEmpty()) {
                    cursor = 0;
                    return;
                }
                cursor = Math.floorMod(cursor, keys.size());

                boolean acquired = false;
                // 한 바퀴 안에서 이미 꽉 찬 유형은 대기열마다 DB에 다시 묻지 않는다.
                Set<AiCallType> exhausted = EnumSet.noneOf(AiCallType.class);
                for (int offset = 0; offset < keys.size() && !acquired; offset++) {
                    int index = (cursor + offset) % keys.size();
                    String key = keys.get(index);
                    Deque<QueuedCall<?>> queue = queues.get(key);
                    if (queue == null || queue.isEmpty()) {
                        queues.remove(key);
                        continue;
                    }
                    QueuedCall<?> next = queue.peekFirst();
                    if (next.future().isCancelled()) {
                        queue.pollFirst();
                        if (queue.isEmpty()) {
                            queues.remove(key);
                        }
                        continue;
                    }
                    if (exhausted.contains(next.type())) {
                        continue;
                    }
                    Optional<String> leased = slots.acquire(next.type(), properties.leaseDuration());
                    if (leased.isEmpty()) {
                        exhausted.add(next.type());
                        continue;
                    }
                    call = next;
                    holder = leased.get();
                    queue.pollFirst();
                    if (queue.isEmpty()) {
                        queues.remove(key);
                    }
                    acquired = true;
                    advanceCursor(key, index);
                }
                if (!acquired) {
                    return;
                }
            }
            execute(call, holder);
        }
    }

    /// 방금 호출을 꺼낸 대기열의 다음 키부터 다음 라운드를 시작한다.
    private void advanceCursor(String key, int previousIndex) {
        List<String> keys = new ArrayList<>(queues.keySet());
        if (keys.isEmpty()) {
            cursor = 0;
            return;
        }
        int current = keys.indexOf(key);
        cursor = current < 0 ? previousIndex % keys.size() : (current + 1) % keys.size();
    }

    private <T> void execute(QueuedCall<T> call, String holder) {
        callers.execute(() -> {
            try {
                if (!call.future().isCancelled()) {
                    call.beforeSend().run();
                    call.future().complete(call.call().get());
                }
            } catch (Throwable error) {
                call.future().completeExceptionally(unwrap(error));
            } finally {
                try {
                    slots.release(call.type(), holder);
                } catch (RuntimeException error) {
                    log.error("AI 호출 슬롯 반납 실패: type={}", call.type(), error);
                }
                dispatcher.execute(this::dispatchSafely);
            }
        });
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException || error instanceof ExecutionException) {
            return error.getCause() == null ? error : error.getCause();
        }
        return error;
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
        callers.shutdownNow();
    }

    private record QueuedCall<T>(AiCallType type, Supplier<T> call, Runnable beforeSend, CompletableFuture<T> future) {}
}
