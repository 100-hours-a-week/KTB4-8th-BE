package eightjbbm.keepgo.auth.authorizationrequest;

import io.sentry.SentryClient;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class OAuthSentryReporter {
    private final SentryClient client;

    public OAuthSentryReporter(OAuthSentryConfiguration.OAuthSentryOptions configuration) {
        var oauthSentryOptions = configuration.value();
        client = oauthSentryOptions.getDsn() == null
                        || oauthSentryOptions.getDsn().isBlank()
                ? null
                : new SentryClient(oauthSentryOptions);
    }

    public void report(String stage, String provider) {
        if (client == null) {
            return;
        }
        try {
            var event = new SentryEvent();
            var message = new Message();
            message.setMessage("OAuth server failure");
            event.setMessage(message);
            event.setLevel(SentryLevel.ERROR);
            event.setTag("stage", stage);
            event.setTag("classification", "server");
            event.setTag("provider", provider);
            client.captureEvent(event, null, null);
        } catch (RuntimeException ignored) {
            // 관측 장애가 사용자 리다이렉트를 막지 않는다.
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
