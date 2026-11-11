package eightjbbm.keepgo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class GoogleOAuthCredentialsTest {
    private static final String PREFIX = "spring.security.oauth2.client.registration.google.";

    @Test
    void requiresBothGoogleCredentials() throws Exception {
        for (String missing : new String[] {"GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET"}) {
            var environment = environment();
            environment
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "credentials",
                            Map.of(
                                    missing.equals("GOOGLE_CLIENT_ID") ? "GOOGLE_CLIENT_SECRET" : "GOOGLE_CLIENT_ID",
                                    "test-value")));

            assertThatThrownBy(() -> environment.getRequiredProperty(
                            PREFIX + (missing.equals("GOOGLE_CLIENT_ID") ? "client-id" : "client-secret")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void usesInjectedGoogleCredentials() throws Exception {
        var environment = environment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "credentials", Map.of("GOOGLE_CLIENT_ID", "test-id", "GOOGLE_CLIENT_SECRET", "test-secret")));

        assertThat(environment.getRequiredProperty(PREFIX + "client-id")).isEqualTo("test-id");
        assertThat(environment.getRequiredProperty(PREFIX + "client-secret")).isEqualTo("test-secret");
    }

    private StandardEnvironment environment() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .addLast(new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yaml"))
                        .getFirst());
        return environment;
    }
}
