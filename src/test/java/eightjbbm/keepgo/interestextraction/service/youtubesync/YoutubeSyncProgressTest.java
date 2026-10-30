package eightjbbm.keepgo.interestextraction.service.youtubesync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("YouTube 동기화 진척률 계산")
class YoutubeSyncProgressTest {

    @Test
    @DisplayName("조회 구간은 고정 가중치이고 나머지는 배치마다 균등하게 증가한다")
    void dividesRemainingProgressEvenlyAcrossBatches() {
        assertThat(YoutubeSyncProgress.afterFetch(20, 10)).isEqualTo(10);
        assertThat(YoutubeSyncProgress.afterBatch(1, 2, 10)).isEqualTo(55);
        assertThat(YoutubeSyncProgress.afterBatch(2, 2, 10)).isEqualTo(100);

        assertThat(YoutubeSyncProgress.afterBatch(1, 3, 10)).isEqualTo(40);
        assertThat(YoutubeSyncProgress.afterBatch(2, 3, 10)).isEqualTo(70);
        assertThat(YoutubeSyncProgress.afterBatch(3, 3, 10)).isEqualTo(100);
    }

    @Test
    @DisplayName("분석 대상이 없으면 조회 직후 100이다")
    void completesImmediatelyWithoutTargets() {
        assertThat(YoutubeSyncProgress.afterFetch(0, 10)).isEqualTo(100);
        assertThat(YoutubeSyncProgress.afterBatch(0, 0, 10)).isEqualTo(100);
    }

    @Test
    @DisplayName("배치가 늘어나도 100을 넘지 않는다")
    void neverExceedsOneHundred() {
        assertThat(YoutubeSyncProgress.afterBatch(5, 3, 10)).isEqualTo(100);
        assertThat(YoutubeSyncProgress.afterBatch(1, 1, 100)).isEqualTo(100);
    }
}
