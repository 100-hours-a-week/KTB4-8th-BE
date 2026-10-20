package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RecommendationServiceV2 {
    public static final long SSE_TIMEOUT_LIMIT = 130_000L;
    private final AiServerRecommendApiService aiServerRecommendApiService;
    private final StringRedisTemplate redis;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SseEmitter recommendOutingCourse(
            Long memberId
    ) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_LIMIT);
        AtomicBoolean disconnected = new AtomicBoolean();

        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onError(e -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
/*
        try {
            executor.execute(() -> run());
        } catch (RejectedExecutionException e) {
            finish();
        }


 */
        return emitter;
    }
/*
    private void run(

    ) {
        Future<RecommendCourseResponse> task = null;
        try {
            task = executor.submit(() -> aiServerRecommendApiService.recommendCourses());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);

            while (!disconnected.get()) {

            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (task != null) {
                task.cancel(true);
            }
            finish();
        }
    }

    private void finish(
            String id,
            String key,
            SseEmitter emitter,
            String event
    ) {
        try {
            String selected = redis.execute(FINISH, List.of(key), id, event);
            SseEmitter.SseEventBuilder response = SseEmitter.event().id(id);

            switch (selected) {
                case "completed" -> response
                        .name("outing-course-recommendation.success")
                        .data(
                                null,
                                MediaType.APPLICATION_JSON);
                case "cancelled" -> response
                        .name("outing-course-recommendation.stopped")
                        .data("");
                case "error" -> response
                        .name(null)
                        .data(
                                null,
                                MediaType.APPLICATION_PROBLEM_JSON
                        );
                default -> throw new IllegalStateException();
            }
            emitter.send(response);
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
    */

    public boolean cancelOutingCourseRecommendation(
            Long memberId
    ) {
        /*
        return Long.valueOf(1).equals(redis.execute(
                CANCEL, List.of("recommendation:" + memberId)));

         */
        return false;
    }

}
