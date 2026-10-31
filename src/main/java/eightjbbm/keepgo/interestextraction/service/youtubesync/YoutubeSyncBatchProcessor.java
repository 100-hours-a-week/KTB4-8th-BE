package eightjbbm.keepgo.interestextraction.service.youtubesync;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import eightjbbm.keepgo.util.client.ai.job.AiRequestException;
import eightjbbm.keepgo.util.client.ai.job.VideoAnalysisResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 동기화 하나의 배치들을 AI 호출 게이트웨이에 모두 제출하고, 결과가 나온 배치부터 하나씩 커밋한다.
///
/// 영상 분석 결과는 시스템 전체에서 공유한다. 이미 끝난 결과가 있는 영상은 AI에 보내지 않고 재사용하며,
/// 전송 직전에 영상별 분석 실행권을 얻지 못한 영상은 진행 중인 분석의 결과를 기다려 공유한다.
///
/// YouTube 조회와 AI 호출은 트랜잭션 밖에서 하고, 커밋만 배치 단위 트랜잭션으로 수행한다.
/// 배치 하나의 AI 호출이 실패해도 그 영상만 FAILED로 기록하고 다음 배치를 계속 처리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncBatchProcessor {

    private static final int BATCH_SIZE = 10;

    /// 합류한 분석을 기다리는 스레드. 기다리는 동안에도 실행권을 얻은 분석의 결과 커밋은 막히지 않아야 한다.
    private static final Executor WAITERS = Executors.newVirtualThreadPerTaskExecutor();

    private final AiCallGateway aiCallGateway;
    private final YoutubeSyncBatchWriter batchWriter;
    private final VideoAnalysisManager videoAnalysisManager;
    private final SharedVideoAnalysisService sharedAnalysis;

    /// 커밋할 한 덩어리. `last`인 덩어리가 커밋되면 배치 하나가 끝난 것으로 진척도를 올린다.
    private record Completion(BatchAnalysis analysis, List<SharedVideoResult> shared, boolean last) {}

    /// 배치 하나가 전송 직전에 영상별로 나뉜 결과. 게이트웨이 스레드가 쓰고 결과 Future가 끝난 뒤 읽는다.
    private static final class BatchExecutionRights {
        private final List<String> granted = new CopyOnWriteArrayList<>();
        private final List<SharedVideoResult> reused = new CopyOnWriteArrayList<>();
        private final List<String> joined = new CopyOnWriteArrayList<>();
    }

    public void process(Long syncId, String instanceId, Long memberId, List<Video> targets, int fetchWeight) {
        List<String> videoIds = targets.stream().map(Video::getId).toList();
        Map<String, SharedVideoResult> reusable = sharedAnalysis.findCompleted(videoIds);
        List<List<String>> batches = batches(
                videoIds.stream().filter(id -> !reusable.containsKey(id)).toList());
        int total = batches.size() + (reusable.isEmpty() ? 0 : 1);
        if (total == 0) {
            return;
        }

        BlockingQueue<Completion> completed = new LinkedBlockingQueue<>();
        List<CompletableFuture<VideoAnalysisResponse>> submitted = new ArrayList<>();
        try {
            if (!reusable.isEmpty()) {
                completed.add(new Completion(null, List.copyOf(reusable.values()), true));
            }
            for (List<String> batch : batches) {
                submitted.add(submit(syncId, batch, completed));
            }

            for (int done = 0; done < total; ) {
                Completion part = await(completed);
                if (part.last()) {
                    done++;
                }
                commit(syncId, instanceId, memberId, part, YoutubeSyncProgress.afterBatch(done, total, fetchWeight));
            }
        } finally {
            // sweeper가 이미 마감한 동기화라면 아직 전송되지 않은 배치를 시작하지 않는다.
            submitted.forEach(future -> future.cancel(false));
            // 중간에 끝나면 이미 전송한 배치의 IN_PROGRESS 행을 커밋할 곳이 없다. 정상 종료면 바뀌는 행이 없다.
            // 이 행을 실패로 마감해야 합류해 기다리던 다른 동기화도 끝난다.
            failInProgress(syncId);
        }
    }

    private CompletableFuture<VideoAnalysisResponse> submit(
            Long syncId, List<String> batch, BlockingQueue<Completion> completed) {
        BatchExecutionRights rights = new BatchExecutionRights();
        CompletableFuture<VideoAnalysisResponse> future = aiCallGateway.submitVideoAnalysis(
                syncId, batch, requested -> acquireExecutionRight(syncId, requested, rights));
        future.handleAsync(
                (response, error) -> {
                    finish(batch, rights, response, error, completed);
                    return null;
                },
                WAITERS);
        return future;
    }

    /// 전송 직전에 영상별로 분석 실행권을 얻고, 얻은 영상만 AI에 보낸다.
    private List<String> acquireExecutionRight(Long syncId, List<String> requested, BatchExecutionRights rights) {
        for (String videoId : requested) {
            SharedVideoAnalysisService.AnalysisExecutionRight outcome =
                    sharedAnalysis.acquireExecutionRight(syncId, videoId);
            if (outcome.granted()) {
                rights.granted.add(videoId);
            } else if (outcome.reused() != null) {
                rights.reused.add(outcome.reused());
            } else {
                rights.joined.add(videoId);
            }
        }
        return List.copyOf(rights.granted);
    }

    /// 실행권을 얻은 영상의 결과를 먼저 커밋하게 넘기고, 합류한 영상은 그 뒤에 기다려 넘긴다.
    ///
    /// 합류 대기가 실행권을 얻은 영상의 커밋을 막으면, 서로의 영상을 기다리는 두 동기화가 교착한다.
    private void finish(
            List<String> batch,
            BatchExecutionRights rights,
            VideoAnalysisResponse response,
            Throwable error,
            BlockingQueue<Completion> completed) {
        boolean waiting = !rights.joined.isEmpty();
        try {
            publishGranted(batch, rights, response, error, waiting, completed);
        } catch (RuntimeException unexpected) {
            // 결과를 넘기지 못하면 커밋 루프가 영원히 기다리므로 이 동기화가 실행권을 얻은 영상만 실패로 넘긴다.
            // 재사용·합류한 영상은 다른 동기화의 이력이므로 새 FAILED 이력을 만들지 않는다.
            log.error("YouTube 동기화 배치 결과 정리 실패: videoCount={}", batch.size(), unexpected);
            completed.add(new Completion(
                    BatchAnalysis.failed(undecidedOrGranted(batch, rights)), List.copyOf(rights.reused), !waiting));
        }

        if (waiting) {
            completed.add(new Completion(null, awaitJoined(rights.joined), true));
        }
    }

    private void publishGranted(
            List<String> batch,
            BatchExecutionRights rights,
            VideoAnalysisResponse response,
            Throwable error,
            boolean waiting,
            BlockingQueue<Completion> completed) {
        // 실행권 판정을 마치지 못한 영상도 이 배치와 함께 실패한다.
        List<String> granted = error == null ? List.copyOf(rights.granted) : undecidedOrGranted(batch, rights);
        BatchAnalysis analysis = granted.isEmpty() ? null : analyze(granted, response, error);
        if (analysis != null || !rights.reused.isEmpty() || !waiting) {
            completed.add(new Completion(analysis, List.copyOf(rights.reused), !waiting));
        }
    }

    /// 재사용·합류로 판정된 영상을 뺀, 이 동기화가 분석 실행권을 얻었거나 아직 판정받지 못한 영상.
    private static List<String> undecidedOrGranted(List<String> batch, BatchExecutionRights rights) {
        return batch.stream()
                .filter(id -> !rights.joined.contains(id)
                        && rights.reused.stream()
                                .noneMatch(result -> result.videoId().equals(id)))
                .toList();
    }

    private List<SharedVideoResult> awaitJoined(List<String> videoIds) {
        try {
            return List.copyOf(sharedAnalysis.awaitResolved(videoIds).values());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.error("공유 영상 분석 대기가 중단되었습니다: videoCount={}", videoIds.size(), interrupted);
        } catch (RuntimeException error) {
            log.error("공유 영상 분석 대기 실패: videoCount={}", videoIds.size(), error);
        }
        return videoIds.stream().map(SharedVideoResult::failed).toList();
    }

    private void failInProgress(Long syncId) {
        try {
            videoAnalysisManager.failInProgress(syncId);
        } catch (RuntimeException error) {
            log.error("남은 분석 중 기록 정리 실패: syncId={}", syncId, error);
        }
    }

    /// AI 호출 실패는 배치 전체를 FAILED로 만들고 예외를 밖으로 던지지 않는다.
    ///
    /// 구조 오류는 `INVALID_AI_RESPONSE`, 그 밖의 전송·HTTP·시간 초과는 해당 오류 종류를
    /// 분석 이력의 실패 코드로 남긴다. 이전 분석 계약으로 되돌아가는 fallback은 없다.
    private BatchAnalysis analyze(List<String> videoIds, VideoAnalysisResponse response, Throwable error) {
        if (error instanceof AiRequestException failure) {
            log.error("AI 배치 분석 실패: videoCount={}, kind={}", videoIds.size(), failure.kind(), failure);
            return BatchAnalysis.failed(videoIds, failure.kind().name());
        }
        if (error != null) {
            log.error("AI 배치 분석 실패: videoCount={}", videoIds.size(), error);
            return BatchAnalysis.failed(videoIds);
        }
        try {
            return BatchAnalysis.from(videoIds, response);
        } catch (RuntimeException failure) {
            log.error("AI 배치 분석 실패: videoCount={}", videoIds.size(), failure);
            return BatchAnalysis.failed(videoIds);
        }
    }

    private Completion await(BlockingQueue<Completion> completed) {
        try {
            return completed.take();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube 동기화 배치 대기가 중단되었습니다", error);
        }
    }

    private void commit(Long syncId, String instanceId, Long memberId, Completion part, int progress) {
        BatchAnalysis analysis = part.analysis() == null ? new BatchAnalysis(List.of()) : part.analysis();
        try {
            batchWriter.commit(syncId, instanceId, memberId, analysis, part.shared(), progress);
        } catch (SyncFencedException fenced) {
            // sweeper가 이미 실패로 마감한 동기화다. 남은 배치를 더 쓰지 않는다.
            throw fenced;
        } catch (RuntimeException error) {
            log.error(
                    "YouTube 동기화 배치 커밋 실패: syncId={}, videoCount={}",
                    syncId,
                    analysis.processedCount() + part.shared().size(),
                    error);
            if (analysis.processedCount() > 0) {
                batchWriter.recordFailed(syncId, instanceId, analysis.videoIds(), progress);
            }
            if (!part.shared().isEmpty()) {
                batchWriter.recordSharedFailed(syncId, instanceId, part.shared().size(), progress);
            }
        }
    }

    private List<List<String>> batches(List<String> videoIds) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < videoIds.size(); i += BATCH_SIZE) {
            batches.add(List.copyOf(videoIds.subList(i, Math.min(i + BATCH_SIZE, videoIds.size()))));
        }
        return batches;
    }
}
