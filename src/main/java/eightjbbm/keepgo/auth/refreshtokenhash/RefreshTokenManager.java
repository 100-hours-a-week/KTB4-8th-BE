package eightjbbm.keepgo.auth.refreshtokenhash;

import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenHash;
import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenHashProperties;
import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenHashRepository;
import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenState;
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
@EnableConfigurationProperties({
        RefreshTokenHashProperties.class
})
public class RefreshTokenManager {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RefreshTokenHashRepository refreshTokenHashRepository;
    private final RefreshTokenHashProperties refreshTokenHashProperties;

    public String issue(Long memberId) {
        String refreshToken = issueInternal();
        refreshTokenHashRepository.save(
                new RefreshTokenHash(
                        memberId,
                        RefreshTokenState.ACTIVE,
                        sha256(refreshToken),
                        Instant.now().plus(refreshTokenHashProperties.ttl())
                ));
        return refreshToken;
    }

    public Optional<RefreshTokenHash> get(String refreshToken) {
        return refreshTokenHashRepository.findById(
                sha256(refreshToken));
    }

    public String rotate(String refreshToken) {
        var refreshTokenHash = refreshTokenHashRepository
                .findById(sha256(refreshToken))
                .orElseThrow();
        refreshTokenHashRepository.save(
                new RefreshTokenHash(
                        refreshTokenHash.memberId(),
                        RefreshTokenState.USED,
                        refreshTokenHash.hash(),
                        refreshTokenHash.expiredAt()));
        return issue(refreshTokenHash.memberId());
    }

    public void revoke(String refreshToken) {
        refreshTokenHashRepository.findById(sha256(refreshToken))
                .ifPresent(current ->
                        refreshTokenHashRepository.save(
                                new RefreshTokenHash(
                                        current.memberId(),
                                        RefreshTokenState.REVOKED,
                                        current.hash(),
                                        current.expiredAt())));
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
