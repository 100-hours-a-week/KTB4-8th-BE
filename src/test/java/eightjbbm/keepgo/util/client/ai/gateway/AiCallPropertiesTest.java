package eightjbbm.keepgo.util.client.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("AI 호출 설정")
class AiCallPropertiesTest {

    private static AiCallProperties withStreamIdle(Duration streamIdleTimeout) {
        return new AiCallProperties(
                3,
                1,
                Duration.ofSeconds(150),
                Duration.ofMillis(200),
                Duration.ofSeconds(130),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                streamIdleTimeout);
    }

    @Test
    @DisplayName("streamIdleTimeout 기본값은 analysisTimeout보다 짧다")
    void defaultStreamIdleTimeoutIsShorterThanAnalysisTimeout() {
        new ApplicationContextRunner().withUserConfiguration(Enabled.class).run(context -> {
            var properties = context.getBean(AiCallProperties.class);
            assertThat(properties.streamIdleTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.streamIdleTimeout()).isLessThan(properties.analysisTimeout());
        });
    }

    @Test
    @DisplayName("streamIdleTimeout 값을 설정으로 바꿀 수 있다")
    void bindsStreamIdleTimeout() {
        new ApplicationContextRunner()
                .withUserConfiguration(Enabled.class)
                .withPropertyValues("keepgo.ai-call.stream-idle-timeout=45s")
                .run(context -> assertThat(
                                context.getBean(AiCallProperties.class).streamIdleTimeout())
                        .isEqualTo(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("streamIdleTimeout이 analysisTimeout 이상이면 거부한다")
    void rejectsStreamIdleTimeoutNotShorterThanAnalysisTimeout() {
        assertThatThrownBy(() -> withStreamIdle(Duration.ofSeconds(130)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamIdleTimeout");
        assertThatThrownBy(() -> withStreamIdle(Duration.ofSeconds(131))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("streamIdleTimeout이 0 이하이면 거부한다")
    void rejectsNonPositiveStreamIdleTimeout() {
        assertThatThrownBy(() -> withStreamIdle(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withStreamIdle(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @EnableConfigurationProperties(AiCallProperties.class)
    static class Enabled {}
}
