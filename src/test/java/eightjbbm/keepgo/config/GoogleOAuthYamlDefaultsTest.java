package eightjbbm.keepgo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Google OAuth yaml 기본값")
class GoogleOAuthYamlDefaultsTest {
    private static final String PREFIX = "spring.security.oauth2.client.registration.google.";

    @Test
    @DisplayName("client-id와 client-secret은 기본값 없이 환경변수만 참조한다")
    void referencesEnvironmentVariablesWithoutDefaults() throws IOException {
        var source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))
                .getFirst();

        assertThat(source.getProperty(PREFIX + "client-id")).isEqualTo("${GOOGLE_CLIENT_ID}");
        assertThat(source.getProperty(PREFIX + "client-secret")).isEqualTo("${GOOGLE_CLIENT_SECRET}");
    }
}
