package eightjbbm.keepgo.auth.refreshtokenhash;

import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

@Component
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
        var refreshTokenHash = refreshTokenManager.get(refreshToken)
                .orElseThrow(
                        () -> new OAuth2AuthenticationException(
                                OAuth2ErrorCodes.INVALID_GRANT));
        if (refreshTokenHash.state() != RefreshTokenState.ACTIVE
                || refreshTokenHash.isExpired()) {
            throw new OAuth2AuthenticationException(
                    OAuth2ErrorCodes.INVALID_GRANT
            );
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return RefreshAuthentication.class.isAssignableFrom(authentication);
    }
}
