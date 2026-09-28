package eightjbbm.keepgo.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFilter;

import java.util.Arrays;

public class RefreshFilter extends AuthenticationFilter {

    public RefreshFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager, RefreshFilter::convert);
    }

    private static Authentication convert(HttpServletRequest request) {
        String refreshToken = Arrays.stream(request.getCookies())
                .filter(cookie -> cookie
                        .getName()
                        .equals("refresh-token")
                )
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
        return RefreshAuthentication.authenticationToken(refreshToken);
    }
}
