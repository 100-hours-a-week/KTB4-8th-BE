package eightjbbm.keepgo.auth.authorizationrequest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthFailureHandler extends OncePerRequestFilter implements AuthenticationFailureHandler {
    private final AuthorizationRequestProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/oauth2/authorization/") && !path.startsWith("/login/oauth2/code/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException exception) {
            log.error("OAuth failure: stage=filter classification=server provider={}", properties.provider());
            response.sendRedirect(properties.failureUrl());
        }
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String stage = request.getRequestURI().startsWith("/oauth2/authorization/") ? "start" : "callback";
        if (exception instanceof OAuth2AuthenticationException oauth
                && ("authorization_request_not_found".equals(oauth.getError().getErrorCode())
                        || "access_denied".equals(oauth.getError().getErrorCode())
                        || "invalid_state_parameter".equals(oauth.getError().getErrorCode()))) {
            log.warn("OAuth failure: stage={} classification=authentication provider={}", stage, properties.provider());
        } else {
            log.error("OAuth failure: stage={} classification=server provider={}", stage, properties.provider());
        }
        response.sendRedirect(properties.failureUrl());
    }
}
