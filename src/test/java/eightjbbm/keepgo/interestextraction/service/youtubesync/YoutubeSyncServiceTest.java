package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.interestextraction.repository.videoanalysis.VideoAnalysisRepository;
import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("YouTube 동기화 heartbeat·sweep 서비스")
class YoutubeSyncServiceTest {

    private final YoutubeSyncRepository repository = mock(YoutubeSyncRepository.class);
    private final VideoAnalysisRepository videoAnalysisRepository = mock(VideoAnalysisRepository.class);
    private final YoutubeSyncService service = new YoutubeSyncService(repository, videoAnalysisRepository);

    @Test
    @DisplayName("heartbeatTimeout이 0 이하면 거부한다")
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> service.failExpired(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heartbeatTimeout");
        assertThatThrownBy(() -> service.failExpired(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heartbeatTimeout");
    }

    @Test
    @DisplayName("1초 미만 timeout은 1초로 올려 만료 조회한다")
    void roundsSubSecondTimeoutUpToOne() {
        when(repository.findExpiredIdsForUpdate(1)).thenReturn(List.of());

        service.failExpired(Duration.ofMillis(500));

        verify(repository).findExpiredIdsForUpdate(1);
    }

    @Test
    @DisplayName("1.5초 timeout은 2초로 올려 만료 조회한다")
    void roundsFractionalTimeoutUp() {
        when(repository.findExpiredIdsForUpdate(2)).thenReturn(List.of());

        service.failExpired(Duration.ofMillis(1500));

        verify(repository).findExpiredIdsForUpdate(2);
    }
}
