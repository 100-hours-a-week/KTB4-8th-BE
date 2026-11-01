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
import org.springframework.dao.CannotAcquireLockException;
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
}
