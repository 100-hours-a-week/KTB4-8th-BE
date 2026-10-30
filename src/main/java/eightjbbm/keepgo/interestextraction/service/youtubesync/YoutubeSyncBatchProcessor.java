package eightjbbm.keepgo.interestextraction.service.youtubesync;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.util.client.ai.analysis.AnalyzeVideoResponseV2;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 동기화 하나의 배치들을 AI 호출 게이트웨이에 모두 제출하고, 응답이 온 배치부터 하나씩 커밋한다.
///
/// YouTube 조회와 AI 호출은 트랜잭션 밖에서 하고, 커밋만 배치 단위 트랜잭션으로 수행한다.
/// 배치 하나의 AI 호출이 실패해도 그 영상만 FAILED로 기록하고 다음 배치를 계속 처리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncBatchProcessor {

    private static final int BATCH_SIZE = 10;

    private final AiCallGateway aiCallGateway;
    private final YoutubeSyncBatchWriter batchWriter;
    private final VideoAnalysisManager videoAnalysisManager;

    public void process(Long syncId, String instanceId, Long memberId, List<Video> targets, int fetchWeight) {
        List<List<String>> batches = batches(targets);
        if (batches.isEmpty()) {
            return;
        }

        BlockingQueue<BatchAnalysis> completed = new LinkedBlockingQueue<>();
        List<CompletableFuture<AnalyzeVideoResponseV2>> submitted = new ArrayList<>();
        for (List<String> batch : batches) {
            CompletableFuture<AnalyzeVideoResponseV2> future =
                    aiCallGateway.submitAnalysis(syncId, batch, () -> videoAnalysisManager.start(syncId, batch));
            submitted.add(future);
            future.handle((response, error) -> completed.add(analyze(batch, response, error)));
        }

        try {
            for (int count = 1; count <= batches.size(); count++) {
                BatchAnalysis analysis = await(completed);
                int progress = YoutubeSyncProgress.afterBatch(count, batches.size(), fetchWeight);
                commit(syncId, instanceId, memberId, analysis, progress);
            }
        } finally {
            // sweeper가 이미 마감한 동기화라면 아직 전송되지 않은 배치를 시작하지 않는다.
            submitted.forEach(future -> future.cancel(false));
        }
    }

    /// AI 호출 실패는 배치 전체를 FAILED로 만들고 예외를 밖으로 던지지 않는다.
    private BatchAnalysis analyze(List<String> videoIds, AnalyzeVideoResponseV2 response, Throwable error) {
        if (error != null) {
            log.error("AI 배치 분석 실패: videoCount={}", videoIds.size(), error);
            return BatchAnalysis.failed(videoIds);
        }
        return BatchAnalysis.from(videoIds, response);
    }

    private BatchAnalysis await(BlockingQueue<BatchAnalysis> completed) {
        try {
            return completed.take();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube 동기화 배치 대기가 중단되었습니다", error);
        }
    }

    private void commit(Long syncId, String instanceId, Long memberId, BatchAnalysis analysis, int progress) {
        try {
            batchWriter.commit(syncId, instanceId, memberId, analysis, progress);
        } catch (SyncFencedException fenced) {
            // sweeper가 이미 실패로 마감한 동기화다. 남은 배치를 더 쓰지 않는다.
            throw fenced;
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 배치 커밋 실패: syncId={}, videoCount={}", syncId, analysis.processedCount(), error);
            batchWriter.recordFailed(syncId, instanceId, analysis.videoIds(), progress);
        }
    }

    private List<List<String>> batches(List<Video> targets) {
        List<String> videoIds = targets.stream().map(Video::getId).toList();
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < videoIds.size(); i += BATCH_SIZE) {
            batches.add(List.copyOf(videoIds.subList(i, Math.min(i + BATCH_SIZE, videoIds.size()))));
        }
        return batches;
    }
}
