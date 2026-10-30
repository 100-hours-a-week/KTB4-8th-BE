package eightjbbm.keepgo.embedding;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// 임베딩 아웃박스 폴러의 동작을 배포 없이 조정할 수 있게 한다.
@ConfigurationProperties(prefix = "keepgo.embedding-outbox")
public record EmbeddingOutboxProperties(
        @DefaultValue("5s") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5m") Duration claimDuration,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue({"1m", "2m", "4m", "8m"}) List<Duration> backoff) {

    public EmbeddingOutboxProperties {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval은 0보다 커야 합니다: " + pollInterval);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다: " + batchSize);
        }
        if (claimDuration == null || claimDuration.isZero() || claimDuration.isNegative()) {
            throw new IllegalArgumentException("claimDuration은 0보다 커야 합니다: " + claimDuration);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts는 0보다 커야 합니다: " + maxAttempts);
        }
        if (backoff == null || backoff.isEmpty()) {
            throw new IllegalArgumentException("backoff는 한 개 이상이어야 합니다");
        }
        if (backoff.stream().anyMatch(duration -> duration == null || duration.isNegative())) {
            throw new IllegalArgumentException("backoff는 음수일 수 없습니다: " + backoff);
        }
        backoff = List.copyOf(backoff);
    }

    /// 실패한 시도 횟수(1부터)에 적용할 대기 시간. 목록보다 시도가 많으면 마지막 값을 재사용한다.
    public Duration backoffAfter(int attempt) {
        int index = Math.min(Math.max(attempt - 1, 0), backoff.size() - 1);
        return backoff.get(index);
    }
}
