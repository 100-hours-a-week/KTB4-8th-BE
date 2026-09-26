package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.rt.RtHashCacheValue;
import eightjbbm.keepgo.auth.rt.RtHashCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenManager {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Duration refreshTokenTtl = Duration.ofDays(7);
    private final RtHashCacheRepository rtHashCacheRepository;

    public String issue(Long memberId) {
        String refreshToken = issueInternal(memberId);
        rtHashCacheRepository.create(
                sha256(refreshToken),
                new RtHashCacheValue(
                        memberId,
                        Instant.now().plus(refreshTokenTtl),
                        RefreshTokenState.ACTIVE
                )
        );
        return refreshToken;
    }

    public String rotate(String refreshToken) {
        String refreshTokenHash = sha256(refreshToken);
        return rtHashCacheRepository.read(refreshTokenHash).map(
                k -> {
                    k.setState(RefreshTokenState.USED);
                    return issue(k.getMemberId());
                }).orElseThrow();
    }

    public void revoke(Long memberId, String refreshToken) {
        String refreshTokenHash = sha256(refreshToken);
        rtHashCacheRepository.read(refreshTokenHash).ifPresent(k -> k.setState(RefreshTokenState.REVOKED));
    }

    private String issueInternal(Long memberId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        return refreshToken;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 Unsupported", e);
        }
    }
}
