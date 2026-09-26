package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.rt.RtHashCacheService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@RequiredArgsConstructor
public class RefreshAuthenticationProvider implements AuthenticationProvider {
    private final RtHashCacheService rtHashCacheService;

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var authRequest = (RefreshAuthentication) authentication;
        var refreshToken = authRequest.getRefreshToken();

        validateRefreshToken(refreshToken);

        return RefreshAuthentication.authenticated();
    }

    private void validateRefreshToken(String refreshToken) {
        var rtValue = rtHashCacheService.getRtByHash(
                    hash(refreshToken)
                )
                .orElseThrow(
                    //RT 없음
                );
        if (!rtValue.getState().equals("ACTIVE")) {
            throw new RuntimeException(); //무효화된 토큰으로 한 번 시도함
        }
        if (rtValue.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException(); //유효 기간 지남, 재로그인
        }
    }

    private String hash(String value) {
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

    @Override
    public boolean supports(Class<?> authentication) {
        return RefreshAuthentication.class.isAssignableFrom(authentication);
    }
}
