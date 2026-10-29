package eightjbbm.keepgo.interestextraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("YouTube 동기화 설정")
class YoutubeSyncPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("heartbeatTimeout 기본값은 60초다")
    void defaultsHeartbeatTimeout() {
        runner.run(context -> assertThat(
                        context.getBean(YoutubeSyncProperties.class).heartbeatTimeout())
                .isEqualTo(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("heartbeatTimeout 설정을 바인딩한다")
    void bindsHeartbeatTimeout() {
        runner.withPropertyValues("keepgo.youtube-sync.heartbeat-timeout=2s")
                .run(context -> assertThat(
                                context.getBean(YoutubeSyncProperties.class).heartbeatTimeout())
                        .isEqualTo(Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("heartbeatTimeout이 0 이하면 설정 바인딩을 거부한다")
    void rejectsNonPositiveHeartbeatTimeout() {
        runner.withPropertyValues("keepgo.youtube-sync.heartbeat-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(YoutubeSyncProperties.class)
    static class TestConfig {}
}
