package eightjbbm.keepgo.auth.rt;

import eightjbbm.keepgo.auth.RefreshTokenState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class RtHashCacheValue {
    private Long memberId;
    private Instant expiresAt;
    @Setter
    private RefreshTokenState state;
}
