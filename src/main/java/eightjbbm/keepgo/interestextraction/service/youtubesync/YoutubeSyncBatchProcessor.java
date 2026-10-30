package eightjbbm.keepgo.interestextraction.service.youtubesync;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.client.ai.analysis.AiServerService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 동기화 하나의 배치들을 병렬로 분석하고, 응답이 온 배치부터 하나씩 커밋한다.
///
/// YouTube 조회와 AI 호출은 트랜잭션 밖에서 하고, 커밋만 배치 단위 트랜잭션으로 수행한다.
/// 배치 하나의 AI 호출이 실패해도 그 영상만 FAILED로 기록하고 다음 배치를 계속 처리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncBatchProcessor {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_PARALLEL_AI_CALLS = 10;

    private final AiServerService aiServerService;
    private final YoutubeSyncBatchWriter batchWriter;

    public void process(Long syncId, String instanceId, Long memberId, List<Video> targets, int fetchWeight) {
        List<List<String>> batches = batches(targets);
        if (batches.isEmpty()) {
            return;
        }

        // 전역 동시 호출 상한은 AI 호출 게이트웨이 티켓에서 다룬다. 지금은 동기화마다 병렬 호출을 제한한다.
        var executor = Executors.newFixedThreadPool(MAX_PARALLEL_AI_CALLS);
        try {
            var completion = new ExecutorCompletionService<BatchAnalysis>(executor);
            for (List<String> batch : batches) {
                completion.submit(() -> analyze(batch));
            }

            for (int completed = 1; completed <= batches.size(); completed++) {
                BatchAnalysis analysis = await(completion);
                int progress = YoutubeSyncProgress.afterBatch(completed, batches.size(), fetchWeight);
                commit(syncId, instanceId, memberId, analysis, progress);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /// AI 호출 실패는 배치 전체를 FAILED로 만들고 예외를 밖으로 던지지 않는다.
    private BatchAnalysis analyze(List<String> videoIds) {
        try {
            return BatchAnalysis.from(videoIds, aiServerService.analyzeVideoV2(videoIds));
        } catch (RuntimeException error) {
            log.error("AI 배치 분석 실패: videoCount={}", videoIds.size(), error);
            return BatchAnalysis.failed(videoIds);
        }
    }

    private BatchAnalysis await(ExecutorCompletionService<BatchAnalysis> completion) {
        try {
            return completion.take().get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("YouTube 동기화 배치 대기가 중단되었습니다", error);
        } catch (ExecutionException error) {
            throw new IllegalStateException("YouTube 동기화 배치 처리에 실패했습니다", error.getCause());
        }
    }

    private void commit(Long syncId, String instanceId, Long memberId, BatchAnalysis analysis, int progress) {
        try {
            List<OutingGuide> createdGuides = batchWriter.commit(syncId, instanceId, memberId, analysis, progress);
            embed(createdGuides);
        } catch (SyncFencedException fenced) {
            // sweeper가 이미 실패로 마감한 동기화다. 남은 배치를 더 쓰지 않는다.
            throw fenced;
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 배치 커밋 실패: syncId={}, videoCount={}", syncId, analysis.processedCount(), error);
            batchWriter.recordFailed(syncId, instanceId, analysis.videoIds(), progress);
        }
    }

    /// 임베딩은 배치 커밋 뒤 트랜잭션 밖에서 호출한다. 실패해도 분석 결과에는 영향을 주지 않는다.
    private void embed(List<OutingGuide> createdGuides) {
        if (createdGuides.isEmpty()) {
            return;
        }
        try {
            aiServerService.embedPlaces(createdGuides);
        } catch (RuntimeException error) {
            log.warn("YouTube 동기화 임베딩 호출 실패(무시): guideCount={}", createdGuides.size(), error);
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
