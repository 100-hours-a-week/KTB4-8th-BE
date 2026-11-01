package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysis;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisManager;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisResultRepository;
import eightjbbm.keepgo.util.client.ai.gateway.AiCallProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("공유 영상 분석 실행권 판정")
class SharedVideoAnalysisServiceTest {

    private final VideoAnalysisRepository repository = mock(VideoAnalysisRepository.class);
    private final SharedVideoAnalysisService service = new SharedVideoAnalysisService(
            repository,
            mock(VideoAnalysisResultRepository.class),
            mock(VideoAnalysisManager.class),
            mock(AiCallProperties.class));

    @Test
    @DisplayName("잠금 충돌 뒤 새 삽입으로 다시 판정한다")
    void retriesAfterLockConflict() {
        VideoAnalysis row = new VideoAnalysis("v0", 1L);
        ReflectionTestUtils.setField(row, "id", 10L);
        when(repository.saveAndFlush(any()))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenReturn(row);
        when(repository.findAllByVideoIdIn(anyCollection())).thenReturn(List.of(row));

        assertThat(service.acquireExecutionRight(1L, "v0").state())
                .isEqualTo(SharedVideoAnalysisService.ExecutionState.GRANTED);
        ArgumentCaptor<VideoAnalysis> inserted = ArgumentCaptor.forClass(VideoAnalysis.class);
        verify(repository, times(2)).saveAndFlush(inserted.capture());
        assertThat(inserted.getAllValues().get(0))
                .isNotSameAs(inserted.getAllValues().get(1));
    }

    @Test
    @DisplayName("잠금 충돌 뒤 재시도에서 진행 중 유일 키를 위반하면 합류한다")
    void joinsWhenRetryViolatesInProgressUniqueKey() {
        when(repository.saveAndFlush(any()))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry 'v0' for key 'video_analysis.uk_video_analysis_in_progress_video'"));

        assertThat(service.acquireExecutionRight(1L, "v0").state())
                .isEqualTo(SharedVideoAnalysisService.ExecutionState.JOINED);
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("잠금 충돌이 계속되면 정해진 횟수 뒤 예외를 던진다")
    void throwsWhenLockConflictPersists() {
        when(repository.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock"));

        assertThatThrownBy(() -> service.acquireExecutionRight(1L, "v0"))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(repository, times(3)).saveAndFlush(any());
    }

    @Test
    @DisplayName("재시도 대기 중 인터럽트되면 재시도를 멈추고 예외를 던지며 인터럽트 상태를 유지한다")
    void stopsRetryingWhenInterrupted() {
        when(repository.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock"));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.acquireExecutionRight(1L, "v0"))
                    .isInstanceOf(CannotAcquireLockException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(repository, times(1)).saveAndFlush(any());
        } finally {
            Thread.interrupted();
        }
    }
}
