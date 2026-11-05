package eightjbbm.keepgo.auth.accesstokenblacklist.repository;

import java.io.Serializable;
import java.time.Instant;

public record AccessTokenBlacklisted(String jti, Instant expiredAt) implements Serializable {
    public boolean isExpired() {
        // 현재 시각이 만료 시각과 정확히 일치할 때에도 만료로 처리
        return !expiredAt.isAfter(Instant.now());
    }
}
