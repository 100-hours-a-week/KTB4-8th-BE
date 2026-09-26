package eightjbbm.keepgo.auth.rt;

import eightjbbm.keepgo.auth.RefreshTokenState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RtHashCacheService {
    private final RtHashCacheRepository rtHashCacheRepository;
    private final Duration refreshTokenTtl = Duration.ofDays(7);

    public Optional<RtHashCacheValue> getRtByHash(String hash) {
        return rtHashCacheRepository.read(hash);
    }

    public void loadSession(Long memberId, String refreshToken) {
        rtHashCacheRepository.create(refreshToken);
        rtHashCacheRepository.update(
                refreshToken,
                new RtHashCacheValue(
                        memberId,
                        Instant.now().plus(refreshTokenTtl),
                        RefreshTokenState.ACTIVE
                )
        );
    }


    public void revokeSession(String refreshTokenHash) {
        var refreshTokenValue = rtHashCacheRepository.read(refreshTokenHash).orElseThrow();
        refreshTokenValue.setState();
    }
}
