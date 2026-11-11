package eightjbbm.keepgo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

@DisplayName("기본 설정의 로깅 레벨")
class LoggingDefaultsTest {

    @Test
    @DisplayName("LOGGING_LEVEL이 없으면 root 레벨은 INFO다")
    void defaultsToInfoWithoutEnvironmentVariable() throws IOException {
        assertThat(environment(Map.of()).getProperty("logging.level.root")).isEqualTo("INFO");
    }

    @Test
    @DisplayName("LOGGING_LEVEL 환경변수는 기본값보다 우선한다")
    void environmentVariableOverridesDefault() throws IOException {
        var environment = environment(Map.of("LOGGING_LEVEL", "DEBUG"));

        assertThat(environment.getProperty("logging.level.root")).isEqualTo("DEBUG");
    }

    private StandardEnvironment environment(Map<String, Object> variables) throws IOException {
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))
                .forEach(sources::addLast);
        return environment;
    }
}
