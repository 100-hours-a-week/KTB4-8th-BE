package eightjbbm.keepgo.auth;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

import java.time.Instant;

@RequiredArgsConstructor
public class RefreshAuthenticationProvider implements AuthenticationProvider {
    private final RefreshTokenManager refreshTokenManager;

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var authRequest = (RefreshAuthentication) authentication;
        var refreshToken = authRequest.getRefreshToken();

        validateRefreshToken(refreshToken);

        return RefreshAuthentication.authenticated();
    }

    private void validateRefreshToken(String refreshToken) {
        var rtValue = refreshTokenManager.get(refreshToken)
                .orElseThrow(
                        () -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT)
                );
        if (!rtValue.getState().equals(RefreshTokenState.ACTIVE)) {
            throw new RuntimeException(); //무효화된 토큰으로 한 번 시도함
        }
        if (rtValue.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException(); //유효 기간 지남, 재로그인
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RefreshAuthentication.class.isAssignableFrom(authentication);
    }
}
