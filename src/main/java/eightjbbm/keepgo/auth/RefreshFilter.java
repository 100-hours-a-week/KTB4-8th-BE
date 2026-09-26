package eightjbbm.keepgo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@RequiredArgsConstructor
public class RefreshFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        var refreshAuthnToken = convert(request);
        if (refreshAuthnToken == null) {
            //throw some error
        }

        try {
            var authentication = authenticationManager.authenticate(refreshAuthnToken);
            var newContext = SecurityContextHolder.createEmptyContext();
            newContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(newContext);
            filterChain.doFilter(request, response);
            return;
        } catch (AuthenticationException e) {

        }
    }

    private static Authentication convert(HttpServletRequest request) {
        String refreshToken = Arrays.stream(request.getCookies())
                .filter(cookie -> cookie
                        .getName()
                        .equals("refresh-token")
                )
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow();
        return RefreshAuthentication.authenticationToken(refreshToken);
    }
}
