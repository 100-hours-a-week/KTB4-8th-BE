package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("YouTube 동기화 배치 충돌 재시도")
class YoutubeSyncBatchWriterTest {

    private static final BatchAnalysis BATCH = BatchAnalysis.failed(List.of("v0"));

    private final YoutubeSyncBatchCommitService commitService = mock(YoutubeSyncBatchCommitService.class);
    private final YoutubeSyncBatchWriter writer = new YoutubeSyncBatchWriter(commitService);

    @Test
    @DisplayName("유일성 위반이 나면 배치 트랜잭션을 다시 실행한다")
    void retriesUniqueViolation() {
        doThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23000", 1062)))
                .doNothing()
                .when(commitService)
                .commit(anyLong(), anyString(), anyLong(), eq(BATCH), eq(List.of()), anyInt());

        writer.commit(1L, "instance-a", 2L, BATCH, 100);

        verify(commitService, times(2)).commit(1L, "instance-a", 2L, BATCH, List.of(), 100);
    }

    @Test
    @DisplayName("교착상태가 두 번 재시도 후에도 이어지면 예외를 던진다")
    void givesUpAfterTwoRetries() {
        doThrow(new CannotAcquireLockException("deadlock", new SQLException("deadlock", "40001", 1213)))
                .when(commitService)
                .commit(anyLong(), anyString(), anyLong(), eq(BATCH), eq(List.of()), anyInt());

        assertThatThrownBy(() -> writer.commit(1L, "instance-a", 2L, BATCH, 100))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(commitService, times(3)).commit(1L, "instance-a", 2L, BATCH, List.of(), 100);
    }

    @Test
    @DisplayName("MySQL 교착상태 오류 코드도 재시도한다")
    void retriesMysqlDeadlockCode() {
        assertThat(YoutubeSyncBatchWriter.isRetryable(
                        new IllegalStateException("wrapped", new SQLException("deadlock", "40001", 1213))))
                .isTrue();
    }

    @Test
    @DisplayName("재시도할 수 없는 예외는 바로 던진다")
    void doesNotRetryOtherErrors() {
        RuntimeException failure = new IllegalStateException("unexpected");
        doThrow(failure)
                .when(commitService)
                .commit(anyLong(), anyString(), anyLong(), eq(BATCH), eq(List.of()), anyInt());

        assertThatThrownBy(() -> writer.commit(1L, "instance-a", 2L, BATCH, 100))
                .isSameAs(failure);

        verify(commitService, times(1)).commit(anyLong(), anyString(), anyLong(), eq(BATCH), eq(List.of()), anyInt());
    }

    @Test
    @DisplayName("fencing 실패는 재시도하지 않는다")
    void doesNotRetryFencedSync() {
        doThrow(new SyncFencedException(1L))
                .when(commitService)
                .commit(anyLong(), anyString(), anyLong(), eq(BATCH), eq(List.of()), anyInt());

        assertThatThrownBy(() -> writer.commit(1L, "instance-a", 2L, BATCH, 100))
                .isInstanceOf(SyncFencedException.class);

        verify(commitService, times(1)).commit(1L, "instance-a", 2L, BATCH, List.of(), 100);
    }
}
