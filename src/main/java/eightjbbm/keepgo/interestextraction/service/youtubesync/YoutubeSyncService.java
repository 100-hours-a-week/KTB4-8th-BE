package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSync;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncState;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 동기화 행의 트랜잭션 경계. 조정기는 이 서비스 밖에서 실행·SSE를 다룬다.
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncService {

    private static final List<YoutubeSyncStatus> ACTIVE = List.of(YoutubeSyncStatus.QUEUED, YoutubeSyncStatus.RUNNING);
    private static final List<YoutubeSyncStatus> TERMINAL =
            List.of(YoutubeSyncStatus.SUCCEEDED, YoutubeSyncStatus.FAILED);

    private final YoutubeSyncRepository repository;
    private final VideoAnalysisRepository videoAnalysisRepository;

    /// 이 인스턴스가 맡은 QUEUED·RUNNING 동기화의 heartbeat를 DB 시간으로 남긴다.
    @Transactional
    public int heartbeat(String instanceId) {
        return repository.heartbeat(instanceId);
    }

    /// heartbeat가 timeout 이상 끊긴 동기화와 그 동기화의 진행 중 분석만 실패로 마감한다.
    ///
    /// 만료 행을 `FOR UPDATE SKIP LOCKED`로 잠가 여러 인스턴스가 동시에 돌아도 한 번만 처리한다.
    @Transactional
    public int failExpired(Duration timeout) {
        List<Long> expiredIds = repository.findExpiredIdsForUpdate(toTimeoutSeconds(timeout));
        if (expiredIds.isEmpty()) {
            return 0;
        }

        videoAnalysisRepository.markFailedBySyncIds(expiredIds);
        return repository.failExpired(expiredIds, YoutubeSyncError.INTERNAL_ERROR.code());
    }

    /// 0 이하 설정은 만료 판단을 무력화하므로 거부하고, 1초 미만 값은 1초로 올려 자른다.
    private int toTimeoutSeconds(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("heartbeatTimeout은 0보다 커야 합니다: " + timeout);
        }

        long seconds = Math.max(1L, (timeout.toMillis() + 999) / 1000);
        return (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> findActive(Long memberId) {
        return repository
                .findFirstByMemberIdAndStatusInOrderByIdDesc(memberId, ACTIVE)
                .map(YoutubeSyncSnapshot::from);
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> findRecentFinished(Long memberId, Instant finishedAfter) {
        return repository
                .findFirstByMemberIdAndStatusInAndFinishedAtAfterOrderByIdDesc(memberId, TERMINAL, finishedAfter)
                .map(YoutubeSyncSnapshot::from);
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> find(Long syncId) {
        return repository.findById(syncId).map(YoutubeSyncSnapshot::from);
    }

    /// 요청 스레드의 1차 캐시를 우회해 종료 여부를 판단할 최신 상태를 읽는다.
    @Transactional(readOnly = true)
    public Optional<YoutubeSyncState> findState(Long syncId) {
        return repository.findStateById(syncId);
    }

    @Transactional(readOnly = true)
    public long countRunningByInstance(String instanceId) {
        return repository.countByInstanceIdAndStatusIn(instanceId, ACTIVE);
    }

    /// 같은 회원의 동기화가 동시에 생기면 유일 제약이 `DataIntegrityViolationException`으로 알린다.
    @Transactional
    public YoutubeSyncSnapshot create(Long memberId, String instanceId, String traceId) {
        return YoutubeSyncSnapshot.from(repository.saveAndFlush(YoutubeSync.queued(memberId, instanceId, traceId)));
    }

    /// 실행 인스턴스가 소유한 QUEUED 동기화만 RUNNING으로 바꾼다.
    ///
    /// 이미 sweeper가 FAILED로 마감했다면 `SyncFencedException`으로 알린다.
    @Transactional
    public void start(Long syncId, String instanceId, int fetchedCount, int targetCount, int progress) {
        int updated = repository.startFenced(syncId, instanceId, fetchedCount, targetCount, progress, Instant.now());
        if (updated == 0) {
            throw new SyncFencedException(syncId);
        }
    }

    /// 실행 인스턴스가 소유한 RUNNING 동기화만 성공으로 마감한다.
    ///
    /// 이미 종료된 동기화는 건드리지 않는다.
    @Transactional
    public void succeed(Long syncId, String instanceId, int progress) {
        int updated = repository.succeedFenced(syncId, instanceId, progress, Instant.now());
        if (updated == 0) {
            log.warn("이미 종료된 YouTube 동기화는 성공으로 마감하지 않습니다: syncId={}", syncId);
        }
    }

    /// 실행 인스턴스가 소유한 QUEUED·RUNNING 동기화만 실패로 마감한다.
    @Transactional
    public void fail(Long syncId, String instanceId, YoutubeSyncError error) {
        int updated = repository.failFenced(syncId, instanceId, error.code(), Instant.now());
        if (updated == 0) {
            log.warn("이미 종료된 YouTube 동기화는 실패로 마감하지 않습니다: syncId={}", syncId);
        }
    }
}
