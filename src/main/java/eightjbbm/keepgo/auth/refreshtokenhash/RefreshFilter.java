package eightjbbm.keepgo.auth.refreshtokenhash;

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
        Cookie[] cookies = request.getCookies();
        String refreshToken = Arrays.stream(
                cookies == null
                        ? new Cookie[0]
                        : cookies)
                .filter(cookie -> "refresh-token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        OAuth2ErrorCodes.INVALID_GRANT));
        return RefreshAuthentication.authenticationToken(refreshToken);
    }
}
