package eightjbbm.keepgo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Google OAuth 자격증명 기동 검증")
class GoogleOAuthCredentialsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                try {
                    sources.addLast(new YamlPropertySourceLoader()
                            .load("application", new ClassPathResource("application.yaml"))
                            .getFirst());
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });

    @Test
    @DisplayName("두 환경변수가 모두 없으면 기동에 실패하고 누락된 변수 이름을 알린다")
    void failsWhenBothMissing() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("GOOGLE_CLIENT_ID")
                    .hasMessageContaining("GOOGLE_CLIENT_SECRET");
        });
    }

    @Test
    @DisplayName("한 환경변수만 없으면 없는 변수만 알린다")
    void reportsOnlyMissingVariable() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID=test-id").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("GOOGLE_CLIENT_SECRET")
                    .hasMessageNotContaining("GOOGLE_CLIENT_ID");
        });
    }

    @Test
    @DisplayName("환경변수가 빈 값이면 기동에 실패한다")
    void failsWhenBlank() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET= ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("GOOGLE_CLIENT_ID")
                    .hasMessageContaining("GOOGLE_CLIENT_SECRET");
        });
    }

    @Test
    @DisplayName("두 환경변수를 주입하면 기동에 성공하고 값을 사용한다")
    void startsWhenInjected() {
        runner.withPropertyValues("GOOGLE_CLIENT_ID=test-id", "GOOGLE_CLIENT_SECRET=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(GoogleOAuthCredentialsProperties.class);
                    assertThat(properties.clientId()).isEqualTo("test-id");
                    assertThat(properties.clientSecret()).isEqualTo("test-secret");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GoogleOAuthCredentialsProperties.class)
    static class TestConfig {}
}
