package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.rt.RtHashCacheRepository;
import eightjbbm.keepgo.auth.rt.RtHashCacheValue;
import eightjbbm.keepgo.util.cache.RtHashCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(RtHashCacheProperties.class)
public class RefreshTokenManager {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RtHashCacheProperties properties;
    private final RtHashCacheRepository rtHashCacheRepository;

    public String issue(Long memberId) {
        String refreshToken = issueInternal();
        rtHashCacheRepository.create(
                sha256(refreshToken),
                RtHashCacheValue.create(
                        memberId,
                        Instant.now().plus(properties.expireAfterWrite())
                )
        );
        return refreshToken;
    }

    public Optional<RtHashCacheValue> get(String refreshToken) {
        String refreshTokenHash = sha256(refreshToken);
        return rtHashCacheRepository.read(refreshTokenHash);
    }

    public String rotate(String refreshToken) {
        String refreshTokenHash = sha256(refreshToken);
        var rtHash = rtHashCacheRepository.read(refreshTokenHash).orElseThrow();
        rtHash.use();
        rtHashCacheRepository.update(refreshTokenHash, rtHash);
        return issue(rtHash.getMemberId());
    }

    public void revoke(String refreshToken) {
        String refreshTokenHash = sha256(refreshToken);
        var rtHash = rtHashCacheRepository.read(refreshTokenHash).orElseThrow();
        rtHash.revoke();
        rtHashCacheRepository.update(refreshTokenHash, rtHash);
    }

    private String issueInternal() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
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
