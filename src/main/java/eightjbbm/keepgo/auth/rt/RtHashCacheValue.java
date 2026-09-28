package eightjbbm.keepgo.auth.rt;

import eightjbbm.keepgo.auth.RefreshTokenState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Instant;

@Getter
public class RtHashCacheValue {
    private Long memberId;
    private Instant expiresAt;
    private RefreshTokenState state;

    private RtHashCacheValue(Long memberId, Instant expiresAt) {
        this.memberId = memberId;
        this.expiresAt = expiresAt;
    }

    public static RtHashCacheValue create(Long memberId, Instant expiresAt) {
        return new RtHashCacheValue(memberId, expiresAt);
    }

    public void use() {
        this.state = RefreshTokenState.USED;
    }

    public void revoke() {
        this.state = RefreshTokenState.REVOKED;
    }
}
