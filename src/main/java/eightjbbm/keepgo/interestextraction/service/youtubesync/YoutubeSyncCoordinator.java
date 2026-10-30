package eightjbbm.keepgo.interestextraction.service.youtubesync;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.YoutubeSyncProperties;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus;
import eightjbbm.keepgo.interestextraction.service.MemberLikedVideoReader;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.YoutubeVideoValidator;
import eightjbbm.keepgo.util.exception.BadGatewayException;
import eightjbbm.keepgo.util.exception.ConflictException;
import eightjbbm.keepgo.util.exception.InternalServerErrorException;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// 회원의 YouTube 동기화를 시작하거나 실행 중인 동기화에 붙이고, SSE 연결에 진척도를 전달한다.
///
/// 실행 인스턴스와 연결 인스턴스가 달라도 같은 `youtube_sync` 행을 폴링하므로 동작이 같다.
@Slf4j
@Service
public class YoutubeSyncCoordinator {

    private static final long WAIT_POLL_MILLIS = 100;

    private final YoutubeSyncService syncService;
    private final MemberLikedVideoReader memberLikedVideoReader;
    private final YoutubeSyncBatchProcessor batchProcessor;
    private final YoutubeConnectionChecker connectionChecker;
    private final Executor executor;
    private final YoutubeSyncProperties properties;
    private final String instanceId;

    private final Set<YoutubeSyncConnection> connections = ConcurrentHashMap.newKeySet();
    private final Object creationLock = new Object();

    public YoutubeSyncCoordinator(
            YoutubeSyncService syncService,
            MemberLikedVideoReader memberLikedVideoReader,
            YoutubeSyncBatchProcessor batchProcessor,
            YoutubeConnectionChecker connectionChecker,
            @Qualifier("youtubeSyncTaskExecutor") Executor executor,
            YoutubeSyncProperties properties) {
        this.syncService = syncService;
        this.memberLikedVideoReader = memberLikedVideoReader;
        this.batchProcessor = batchProcessor;
        this.connectionChecker = connectionChecker;
        this.executor = executor;
        this.properties = properties;
        this.instanceId = properties.resolvedInstanceId();
    }

    public SseEmitter connect(Long memberId) {
        SyncStart start = startOrAttach(memberId, true);
        if (start.rejection() != null) {
            return reject(start.rejection());
        }

        SseEmitter emitter = attach(start.snapshot());
        if (start.created()) {
            submit(start.snapshot().id(), start.snapshot().memberId());
        }
        return emitter;
    }

    /// 동기화를 시작하거나 이미 실행 중인 동기화에 붙고, 종료 상태가 될 때까지 기다린다.
    ///
    /// V2 API가 같은 조정기를 거쳐 V3와 같은 안정성 보장을 받게 한다. SSE 연결 없이
    /// 행의 상태만 폴링하므로 다른 인스턴스가 실행한 동기화에도 붙을 수 있다.
    /// 최근에 끝난 결과는 SSE 재연결용이므로 재사용하지 않고 새 동기화를 시작한다.
    public void syncAndWait(Long memberId) {
        SyncStart start = startOrAttach(memberId, false);
        if (start.rejection() != null) {
            throw failure(start.rejection());
        }

        if (start.created()) {
            submit(start.snapshot().id(), start.snapshot().memberId());
        }
        awaitTerminal(start.snapshot().id());
    }

    private SyncStart startOrAttach(Long memberId, boolean reuseRecentResult) {
        var active = syncService.findActive(memberId);
        if (active.isPresent()) {
            return new SyncStart(active.get(), false, null);
        }

        if (reuseRecentResult) {
            var recent = syncService.findRecentFinished(memberId, Instant.now().minus(properties.recentResultWindow()));
            if (recent.isPresent()) {
                return new SyncStart(recent.get(), false, null);
            }
        }

        // 스트림을 열기 전에 연동 여부만 확인해야 409를 HTTP 상태로 돌려줄 수 있다.
        // 영상 목록 조회는 스트림이 열린 뒤 실행 인스턴스가 한다.
        connectionChecker.requireConnected(memberId);

        // 같은 인스턴스의 상한 검사와 행 생성을 한 임계 구역에서 처리해 동시에 상한을 넘지 않게 한다.
        synchronized (creationLock) {
            if (syncService.countRunningByInstance(instanceId) >= properties.maxConcurrentSyncs()) {
                return new SyncStart(null, false, YoutubeSyncError.SYNC_BUSY);
            }

            boolean created = true;
            YoutubeSyncSnapshot snapshot;
            try {
                snapshot = syncService.create(
                        memberId, instanceId, UUID.randomUUID().toString());
            } catch (DataIntegrityViolationException duplicate) {
                // 다른 요청(다른 인스턴스 포함)이 먼저 만들었다면 그 동기화에 붙는다.
                created = false;
                snapshot = syncService.findActive(memberId).orElseThrow(ConflictException::new);
            }

            return new SyncStart(snapshot, created, null);
        }
    }

    private void awaitTerminal(Long syncId) {
        // 인스턴스는 살아 있고 실행 스레드만 멈추면 heartbeat가 계속 갱신되어 sweeper가 회수하지 못한다.
        long deadline = System.currentTimeMillis() + properties.emitterTimeoutMs();
        while (true) {
            var state = syncService.findState(syncId).orElseThrow(InternalServerErrorException::new);
            if (state.isTerminal()) {
                if (state.status() == YoutubeSyncStatus.FAILED) {
                    YoutubeSyncError error = YoutubeSyncError.fromCode(state.errorCode());
                    log.warn("YouTube 동기화가 실패로 끝났습니다: syncId={}, code={}", syncId, error);
                    throw failure(error);
                }
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                log.error("YouTube 동기화 종료 대기 시간을 초과했습니다: syncId={}", syncId);
                throw new ServiceUnavailableException();
            }
            sleepBeforePoll(syncId);
        }
    }

    private void sleepBeforePoll(Long syncId) {
        try {
            Thread.sleep(WAIT_POLL_MILLIS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.error("YouTube 동기화 종료 대기가 중단되었습니다: syncId={}", syncId, error);
            throw new InternalServerErrorException();
        }
    }

    private ErrorResponseException failure(YoutubeSyncError error) {
        return switch (error) {
            case YOUTUBE_REAUTHORIZATION_REQUIRED -> new ConflictException();
            case YOUTUBE_FETCH_FAILED -> new BadGatewayException();
            case SYNC_BUSY -> new ServiceUnavailableException();
            case INTERNAL_ERROR -> new InternalServerErrorException();
        };
    }

    /// 실행 중인 동기화의 heartbeat를 주기적으로 남겨 인스턴스 장애를 다른 인스턴스가 감지하게 한다.
    @Scheduled(fixedDelayString = "${keepgo.youtube-sync.heartbeat-interval-ms:10000}")
    public void heartbeat() {
        try {
            syncService.heartbeat(instanceId);
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 heartbeat 기록 실패: instanceId={}", instanceId, error);
        }
    }

    /// heartbeat가 끊긴 동기화를 어느 인스턴스에서든 회수해 FAILED로 마감한다.
    @Scheduled(fixedDelayString = "${keepgo.youtube-sync.sweep-interval-ms:30000}")
    public void sweepExpiredSyncs() {
        try {
            int failed = syncService.failExpired(properties.heartbeatTimeout());
            if (failed > 0) {
                log.warn("heartbeat가 끊긴 YouTube 동기화를 실패 처리했습니다: count={}", failed);
            }
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 만료 처리 실패", error);
        }
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
            var snapshot = syncService.find(connection.syncId());
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

    private void submit(Long syncId, Long memberId) {
        try {
            executor.execute(() -> execute(syncId, memberId));
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 실행을 시작하지 못했습니다: syncId={}", syncId, error);
            syncService.fail(syncId, instanceId, YoutubeSyncError.INTERNAL_ERROR);
        }
    }

    private void execute(Long syncId, Long memberId) {
        List<Video> videos;
        try {
            videos = memberLikedVideoReader.readAll(memberId);
        } catch (RuntimeException error) {
            YoutubeSyncError failure = fetchError(error);
            logFetchFailure(syncId, failure, error);
            syncService.fail(syncId, instanceId, failure);
            return;
        }

        List<Video> targets = distinctById(YoutubeVideoValidator.validate(videos));
        try {
            syncService.start(
                    syncId,
                    instanceId,
                    videos.size(),
                    targets.size(),
                    YoutubeSyncProgress.afterFetch(targets.size(), properties.fetchWeight()));
        } catch (SyncFencedException fenced) {
            log.warn("YouTube 동기화 시작이 차단되었습니다: syncId={}", syncId);
            return;
        }

        try {
            batchProcessor.process(syncId, instanceId, memberId, targets, properties.fetchWeight());
            syncService.succeed(syncId, instanceId, 100);
        } catch (SyncFencedException fenced) {
            // sweeper가 이미 실패로 마감했다. 추가 쓰기 없이 끝낸다.
            log.warn("YouTube 동기화 쓰기가 차단되어 중단합니다: syncId={}", syncId);
        } catch (RuntimeException error) {
            log.error("YouTube 동기화 실행 실패: syncId={}", syncId, error);
            syncService.fail(syncId, instanceId, YoutubeSyncError.INTERNAL_ERROR);
        }
    }

    private void logFetchFailure(Long syncId, YoutubeSyncError failure, RuntimeException error) {
        if (failure == YoutubeSyncError.YOUTUBE_REAUTHORIZATION_REQUIRED) {
            log.warn("YouTube 동기화 재연동 필요: syncId={}", syncId);
        } else {
            log.error("YouTube 동기화 조회 실패: syncId={}", syncId, error);
        }
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

    /// 시작·부착 결과. 거절이면 snapshot은 null이고 created는 false다.
    private record SyncStart(YoutubeSyncSnapshot snapshot, boolean created, YoutubeSyncError rejection) {}
}
