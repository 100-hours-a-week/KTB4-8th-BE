package eightjbbm.keepgo.util.client.ai.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// AI 호출 게이트웨이의 전역 상한·재시도 주기·호출 전체 제한을 배포 없이 조정할 수 있게 한다.
///
/// `analysisTimeout`·`embeddingTimeout`은 전송부터 응답 수신까지의 전체 제한이며 연결 시간을 포함한다.
@ConfigurationProperties(prefix = "keepgo.ai-call")
public record AiCallProperties(
        @DefaultValue("3") int analysisLimit,
        @DefaultValue("1") int embeddingLimit,
        @DefaultValue("150s") Duration leaseDuration,
        @DefaultValue("200ms") Duration retryInterval,
        @DefaultValue("130s") Duration analysisTimeout,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration embeddingTimeout) {

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
        requirePositive(embeddingTimeout, "embeddingTimeout");
        requireWithin(connectTimeout, analysisTimeout, "connectTimeout", "analysisTimeout");
        requireWithin(connectTimeout, embeddingTimeout, "connectTimeout", "embeddingTimeout");
        // 임대가 호출보다 먼저 끝나면 다른 호출이 같은 슬롯을 가져가 상한을 넘는다.
        if (leaseDuration.compareTo(analysisTimeout) <= 0 || leaseDuration.compareTo(embeddingTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "leaseDuration은 analysisTimeout·embeddingTimeout보다 길어야 합니다: " + leaseDuration);
        }
    }

    /// 호출 종류별 전체 제한.
    public Duration timeout(AiCallType type) {
        return type == AiCallType.ANALYSIS ? analysisTimeout : embeddingTimeout;
    }

    private static void requireWithin(Duration inner, Duration outer, String innerName, String outerName) {
        if (inner.compareTo(outer) > 0) {
            throw new IllegalArgumentException(innerName + "은 " + outerName + " 이내여야 합니다: " + inner);
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + duration);
        }
    }
}
