package eightjbbm.keepgo.interestextraction.service.youtubesync;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/// 배치 트랜잭션을 충돌 시 다시 실행한다.
///
/// AI 응답은 이미 메모리에 있으므로 재시도할 때 AI를 다시 호출하지 않는다. 재시도마다
/// [YoutubeSyncBatchCommitService]가 fencing 조건을 다시 확인한다. 유일성 위반과 교착상태는
/// 다른 동기화가 같은 가이드를 먼저 저장했거나 행 잠금이 엉킨 경우이므로, 트랜잭션 전체를
/// 롤백하고 다시 실행하면 기존 가이드를 재사용하거나 잠금을 새로 얻는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeSyncBatchWriter {

    /// 최초 실행 1회와 재시도 2회.
    private static final int MAX_ATTEMPTS = 3;

    private static final Set<Integer> RETRYABLE_MYSQL_ERRORS = Set.of(1062, 1205, 1213);

    private final YoutubeSyncBatchCommitService commitService;

    /// 배치 결과를 저장한다. 새로 만든 가이드의 임베딩 작업도 같은 트랜잭션에 기록한다.
    public void commit(Long syncId, String instanceId, Long memberId, BatchAnalysis batch, int progress) {
        commit(syncId, instanceId, memberId, batch, List.of(), progress);
    }

    /// 배치 결과에 다른 동기화가 끝낸 공유 결과를 함께 반영한다. 공유 결과는 분석 이력을 새로 만들지 않는다.
    public void commit(
            Long syncId,
            String instanceId,
            Long memberId,
            BatchAnalysis batch,
            List<SharedVideoResult> shared,
            int progress) {
        withRetry("commit", () -> commitService.commit(syncId, instanceId, memberId, batch, shared, progress));
    }

    /// 커밋에 실패한 배치를 실패 영상으로만 기록한다. 이미 커밋된 배치는 건드리지 않는다.
    public void recordFailed(Long syncId, String instanceId, List<String> videoIds, int progress) {
        commitService.recordFailed(syncId, instanceId, videoIds, progress);
    }

    /// 공유 결과만 반영하려던 커밋이 실패하면 분석 이력은 건드리지 않고 처리·실패 수만 남긴다.
    public void recordSharedFailed(Long syncId, String instanceId, int videoCount, int progress) {
        commitService.recordSharedFailed(syncId, instanceId, videoCount, progress);
    }

    private void withRetry(String action, Runnable attempt) {
        for (int tries = 1; tries <= MAX_ATTEMPTS; tries++) {
            try {
                attempt.run();
                return;
            } catch (RuntimeException error) {
                if (!isRetryable(error)) {
                    throw error;
                }
                if (tries == MAX_ATTEMPTS) {
                    log.error("YouTube 동기화 배치 {} 충돌 재시도를 모두 소진했습니다: attempts={}", action, MAX_ATTEMPTS, error);
                    throw error;
                }
                log.warn("YouTube 동기화 배치 {} 충돌, 재시도합니다: attempt={}", action, tries, error);
            }
        }
        throw new IllegalStateException("배치 커밋 재시도 상태가 올바르지 않습니다");
    }

    /// 유일성 위반, 교착상태, 잠금 대기 시간 초과만 재시도한다.
    static boolean isRetryable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof PessimisticLockingFailureException) {
                return true;
            }
            if (current instanceof SQLException sql && RETRYABLE_MYSQL_ERRORS.contains(sql.getErrorCode())) {
                return true;
            }
        }
        return false;
    }
}
