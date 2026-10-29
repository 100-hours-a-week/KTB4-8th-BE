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
        @DefaultValue("5m") Duration recentResultWindow) {

    /// 실행 인스턴스 식별자. 설정하지 않으면 프로세스마다 다른 값을 쓴다.
    public String resolvedInstanceId() {
        return instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId;
    }
}
