package eightjbbm.keepgo.interestextraction;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "keepgo.youtube-sync")
public record YoutubeSyncProperties(
        String instanceId,
        @DefaultValue("3") int maxConcurrentSyncs,
        @DefaultValue("15000") long keepAliveMs,
        @DefaultValue("1800000") long emitterTimeoutMs,
        @DefaultValue("10") int fetchWeight,
        @DefaultValue("5m") Duration recentResultWindow,
        @DefaultValue("60s") Duration heartbeatTimeout,
        @DefaultValue("50s") Duration v2WaitTimeout,
        @DefaultValue("500ms") Duration v2WaitPollInterval) {

    public YoutubeSyncProperties {
        requirePositive("heartbeatTimeout", heartbeatTimeout);
        requirePositive("v2WaitTimeout", v2WaitTimeout);
        requirePositive("v2WaitPollInterval", v2WaitPollInterval);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 커야 합니다: " + value);
        }
    }

    /// 실행 인스턴스 식별자. 설정하지 않으면 프로세스마다 다른 값을 쓴다.
    ///
    /// 실행 중인 프로세스마다 고유해야 한다. 재시작해도 같은 값을 쓰면 이미 죽은 행의
    /// heartbeat를 계속 갱신해 만료 처리가 되지 않으므로, 운영에서는 배포마다 새 값을 준다.
    public String resolvedInstanceId() {
        return instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId;
    }
}
