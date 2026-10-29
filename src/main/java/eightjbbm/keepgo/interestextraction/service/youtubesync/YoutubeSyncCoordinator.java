package eightjbbm.keepgo.interestextraction.service.youtubesync;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.YoutubeSyncProperties;
import eightjbbm.keepgo.interestextraction.service.MemberLikedVideoReader;
import eightjbbm.keepgo.interestextraction.service.YoutubeVideoProvider;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.YoutubeVideoAnalyzer;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.YoutubeVideoValidator;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.util.exception.ConflictException;
import eightjbbm.keepgo.util.exception.YoutubeNotConnectedException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// 회원의 YouTube 동기화를 시작하거나 실행 중인 동기화에 붙이고, SSE 연결에 진척도를 전달한다.
///
/// 실행 인스턴스와 연결 인스턴스가 달라도 같은 `youtube_sync` 행을 폴링하므로 동작이 같다.
@Slf4j
@Service
public class YoutubeSyncCoordinator {

    private static final int BATCH_SIZE = 10;

    private final YoutubeSyncStore store;
    private final MemberLikedVideoReader memberLikedVideoReader;
    private final YoutubeVideoProvider youtubeVideoProvider;
    private final YoutubeVideoAnalyzer youtubeVideoAnalyzer;
    private final OutingWishlistSyncService outingWishlistSyncService;
    private final Executor executor;
    private final YoutubeSyncProperties properties;
    private final String instanceId;

    private final Set<YoutubeSyncConnection> connections = ConcurrentHashMap.newKeySet();

    public YoutubeSyncCoordinator(
            YoutubeSyncStore store,
            MemberLikedVideoReader memberLikedVideoReader,
            YoutubeVideoProvider youtubeVideoProvider,
            YoutubeVideoAnalyzer youtubeVideoAnalyzer,
            OutingWishlistSyncService outingWishlistSyncService,
            @Qualifier("youtubeSyncTaskExecutor") Executor executor,
            YoutubeSyncProperties properties) {
        this.store = store;
        this.memberLikedVideoReader = memberLikedVideoReader;
        this.youtubeVideoProvider = youtubeVideoProvider;
        this.youtubeVideoAnalyzer = youtubeVideoAnalyzer;
        this.outingWishlistSyncService = outingWishlistSyncService;
        this.executor = executor;
        this.properties = properties;
        this.instanceId = properties.resolvedInstanceId();
    }

    public SseEmitter connect(Long memberId) {
        var active = store.findActive(memberId);
        if (active.isPresent()) {
            return attach(active.get());
        }

        var recent = store.findRecentFinished(memberId, Instant.now().minus(properties.recentResultWindow()));
        if (recent.isPresent()) {
            return attach(recent.get());
        }

        // 스트림을 열기 전에 연동 여부를 확인해야 409를 HTTP 상태로 돌려줄 수 있다.
        checkYoutubeConnection(memberId);

        if (store.countRunningByInstance(instanceId) >= properties.maxConcurrentSyncs()) {
            return reject(YoutubeSyncError.SYNC_BUSY);
        }

        boolean created = true;
        YoutubeSyncSnapshot snapshot;
        try {
            snapshot = store.create(memberId, instanceId, UUID.randomUUID().toString());
        } catch (DataIntegrityViolationException duplicate) {
            // 다른 요청(다른 인스턴스 포함)이 먼저 만들었다면 그 동기화에 붙는다.
            created = false;
            snapshot = store.findActive(memberId).orElseThrow(ConflictException::new);
        }

        SseEmitter emitter = attach(snapshot);
        if (created) {
            submit(snapshot.id(), snapshot.memberId());
        }
        return emitter;
    }

    /// 모든 연결은 실행 인스턴스와 무관하게 같은 행을 폴링한다.
    @Scheduled(fixedDelayString = "${keepgo.youtube-sync.poll-interval-ms:1000}")
    public void pollConnections() {
        for (YoutubeSyncConnection connection : connections) {
            poll(connection);
        }
    }

    private void poll(YoutubeSyncConnection connection) {
        try {
            var snapshot = store.find(connection.syncId());
            if (snapshot.isEmpty()) {
                remove(connection);
                return;
            }

            if (snapshot.get().isTerminal()) {
                connection.finish(snapshot.get());
                remove(connection);
                return;
            }

            connection.progressIfChanged(snapshot.get());
            connection.keepAliveIfDue();
        } catch (RuntimeException error) {
            log.warn("YouTube 동기화 상태 조회 실패: syncId={}", connection.syncId(), error);
        }
    }

    private SseEmitter attach(YoutubeSyncSnapshot snapshot) {
        var connection =
                new YoutubeSyncConnection(snapshot.id(), properties.emitterTimeoutMs(), properties.keepAliveMs());
        connection.accept(snapshot);

        if (snapshot.isTerminal()) {
            connection.finishAfterAccept(snapshot);
            return connection.emitter();
        }

        connection.emitter().onCompletion(() -> remove(connection));
        connection.emitter().onError(error -> remove(connection));
        connection.emitter().onTimeout(() -> {
            connection.close();
            remove(connection);
        });
        connections.add(connection);
        return connection.emitter();
    }

    private SseEmitter reject(YoutubeSyncError error) {
        var connection = new YoutubeSyncConnection(0L, properties.emitterTimeoutMs(), properties.keepAliveMs());
        connection.reject(error);
        return connection.emitter();
    }

    private void remove(YoutubeSyncConnection connection) {
        connection.close();
        connections.remove(connection);
    }

    private void checkYoutubeConnection(Long memberId) {
        try {
            youtubeVideoProvider.getVideoLists(memberId, null);
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new YoutubeNotConnectedException();
            }
            throw error;
        }
    }

    private void submit(Long syncId, Long memberId) {
        try {
            executor.execute(() -> execute(syncId, memberId));
        } catch (RuntimeException error) {
            log.warn("YouTube 동기화 실행을 시작하지 못했습니다: syncId={}", syncId, error);
            store.fail(syncId, YoutubeSyncError.INTERNAL_ERROR);
        }
    }

    private void execute(Long syncId, Long memberId) {
        List<Video> videos;
        try {
            videos = memberLikedVideoReader.readAll(memberId);
        } catch (RuntimeException error) {
            log.warn("YouTube 동기화 조회 실패: syncId={}", syncId, error);
            store.fail(syncId, fetchError(error));
            return;
        }

        List<Video> targets = distinctById(YoutubeVideoValidator.validate(videos));
        int batchCount = (targets.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        store.start(syncId, videos.size(), targets.size(), fetchProgress(targets.size()));

        AtomicInteger completedBatches = new AtomicInteger();
        try {
            List<OutingGuide> guides = youtubeVideoAnalyzer.analyze(targets, (processedCount, failedCount) -> {
                int completed = completedBatches.incrementAndGet();
                recordBatch(syncId, processedCount, failedCount, batchProgress(completed, batchCount));
            });
            outingWishlistSyncService.sync(memberId, guides);
            store.succeed(syncId, 100);
        } catch (RuntimeException error) {
            log.warn("YouTube 동기화 실행 실패: syncId={}", syncId, error);
            store.fail(syncId, YoutubeSyncError.INTERNAL_ERROR);
        }
    }

    private void recordBatch(Long syncId, int processedCount, int failedCount, int progress) {
        try {
            store.recordBatch(syncId, processedCount, failedCount, progress);
        } catch (RuntimeException error) {
            // 진척도 기록 실패가 분석 결과를 버리지 않게 한다.
            log.warn("YouTube 동기화 진척도 기록 실패: syncId={}", syncId, error);
        }
    }

    private int fetchProgress(int targetCount) {
        return targetCount == 0 ? 100 : Math.min(100, properties.fetchWeight());
    }

    private int batchProgress(int completedBatches, int batchCount) {
        if (batchCount == 0) {
            return 100;
        }
        int remaining = 100 - properties.fetchWeight();
        return Math.min(100, properties.fetchWeight() + (completedBatches * remaining) / batchCount);
    }

    private YoutubeSyncError fetchError(RuntimeException error) {
        if (error instanceof ResponseStatusException status
                && status.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
            return YoutubeSyncError.YOUTUBE_REAUTHORIZATION_REQUIRED;
        }
        return YoutubeSyncError.YOUTUBE_FETCH_FAILED;
    }

    private List<Video> distinctById(List<Video> videos) {
        Map<String, Video> unique = new LinkedHashMap<>();
        for (Video video : videos) {
            unique.putIfAbsent(video.getId(), video);
        }
        return List.copyOf(unique.values());
    }
}
