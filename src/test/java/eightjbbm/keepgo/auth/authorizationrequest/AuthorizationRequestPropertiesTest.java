package eightjbbm.keepgo.auth.authorizationrequest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("OAuth 인증 요청 설정")
class AuthorizationRequestPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    @DisplayName("다른 임시 저장소 설정과 관계없이 기본값은 RDB와 10분이다")
    void defaultsIndependently() {
        runner.withPropertyValues("keepgo.ephemeral.provider=redis").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(AuthorizationRequestProperties.class);
            assertThat(properties.provider()).isEqualTo("rdb");
            assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.secureCookie()).isTrue();
        });
    }

    @Test
    @DisplayName("지원하지 않는 provider는 기동에 실패한다")
    void rejectsUnsupportedProvider() {
        runner.withPropertyValues("keepgo.security.oauth2.authorization-request.provider=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthorizationRequestProperties.class)
    static class Config {}
}
