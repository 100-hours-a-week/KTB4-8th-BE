package eightjbbm.keepgo.auth.authorizationrequest;

import io.sentry.SentryOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthSentryConfiguration.Properties.class)
public class OAuthSentryConfiguration {
    @ConfigurationProperties("sentry")
    public record Properties(String dsn) {}

    @Bean
    io.sentry.EventProcessor oauthFailureLogFilter() {
        return new io.sentry.EventProcessor() {
            @Override
            public io.sentry.SentryEvent process(io.sentry.SentryEvent event, io.sentry.Hint hint) {
                return OAuthFailureHandler.class.getName().equals(event.getLogger())
                                || "oauth.authorization-request.storage".equals(event.getLogger())
                        ? null
                        : event;
            }
        };
    }

    public record OAuthSentryOptions(SentryOptions value) {}

    @Bean
    OAuthSentryOptions oauthSentryOptions(Properties properties) {
        var options = new SentryOptions();
        options.setDsn(properties.dsn());
        options.setAttachStacktrace(false);
        options.setAttachThreads(false);
        return new OAuthSentryOptions(options);
    }
}
