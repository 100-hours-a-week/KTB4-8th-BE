package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSync;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 동기화 행의 트랜잭션 경계. 조정기는 이 클래스 밖에서 실행·SSE를 다룬다.
@Service
@RequiredArgsConstructor
public class YoutubeSyncStore {

    private static final List<YoutubeSyncStatus> ACTIVE = List.of(YoutubeSyncStatus.QUEUED, YoutubeSyncStatus.RUNNING);
    private static final List<YoutubeSyncStatus> TERMINAL =
            List.of(YoutubeSyncStatus.SUCCEEDED, YoutubeSyncStatus.FAILED);

    private final YoutubeSyncRepository repository;

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> findActive(Long memberId) {
        return repository
                .findFirstByMemberIdAndStatusInOrderByIdDesc(memberId, ACTIVE)
                .map(YoutubeSyncSnapshot::from);
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> findRecentFinished(Long memberId, Instant finishedAfter) {
        return repository
                .findFirstByMemberIdAndStatusInAndFinishDatetimeAfterOrderByIdDesc(memberId, TERMINAL, finishedAfter)
                .map(YoutubeSyncSnapshot::from);
    }

    @Transactional(readOnly = true)
    public Optional<YoutubeSyncSnapshot> find(Long syncId) {
        return repository.findById(syncId).map(YoutubeSyncSnapshot::from);
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

    @Transactional
    public void start(Long syncId, int fetchedCount, int targetCount, int progress) {
        repository.findById(syncId).ifPresent(sync -> sync.start(fetchedCount, targetCount, progress));
    }

    @Transactional
    public void recordBatch(Long syncId, int processedCount, int failedCount, int progress) {
        repository.recordBatch(syncId, processedCount, failedCount, progress, Instant.now());
    }

    @Transactional
    public void succeed(Long syncId, int progress) {
        repository.findById(syncId).ifPresent(sync -> sync.succeed(progress));
    }

    @Transactional
    public void fail(Long syncId, YoutubeSyncError error) {
        repository.findById(syncId).ifPresent(sync -> sync.fail(error.code()));
    }
}
