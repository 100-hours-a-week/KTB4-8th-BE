package eightjbbm.keepgo.util.client.ai.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// AI 호출 게이트웨이의 전역 상한과 재시도 주기를 배포 없이 조정할 수 있게 한다.
@ConfigurationProperties(prefix = "keepgo.ai-call")
public record AiCallProperties(
        @DefaultValue("3") int analysisLimit,
        @DefaultValue("1") int embeddingLimit,
        @DefaultValue("150s") Duration leaseDuration,
        @DefaultValue("200ms") Duration retryInterval,
        @DefaultValue("130s") Duration analysisTimeout,
        @DefaultValue("5s") Duration connectTimeout) {

    public AiCallProperties {
        if (analysisLimit <= 0) {
            throw new IllegalArgumentException("analysisLimit은 0보다 커야 합니다: " + analysisLimit);
        }
        if (embeddingLimit <= 0) {
            throw new IllegalArgumentException("embeddingLimit은 0보다 커야 합니다: " + embeddingLimit);
        }
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(retryInterval, "retryInterval");
        requirePositive(analysisTimeout, "analysisTimeout");
        requirePositive(connectTimeout, "connectTimeout");
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + duration);
        }
    }
}
