package eightjbbm.keepgo.auth.authorizationrequest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthorizationRequestProperties.class)
public class SharedAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String COOKIE_PREFIX = "oauth_req_";
    private final AuthorizationRequestStore store;
    private final AuthorizationRequestCodec codec;
    private final AuthorizationRequestProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return recover(request, null, false);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String browser = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = hash(authorizationRequest.getState());
        try {
            store.save(
                    id,
                    codec.encode(authorizationRequest),
                    hash(browser),
                    Instant.now().plus(properties.ttl()));
        } catch (RuntimeException exception) {
            throw new AuthorizationRequestStorageException();
        }
        addCookie(response, COOKIE_PREFIX + id, browser, properties.ttl());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        return recover(request, response, true);
    }

    private OAuth2AuthorizationRequest recover(
            HttpServletRequest request, HttpServletResponse response, boolean consume) {
        String state = request.getParameter("state");
        if (state == null || state.isEmpty() || state.length() > 512) {
            return null;
        }
        String id = hash(state);
        String browser = browserCookie(request, COOKIE_PREFIX + id);
        if (browser == null || browser.length() > 128) {
            return null;
        }
        String json = consume
                ? store.consume(id, hash(browser), Instant.now())
                : store.find(id, hash(browser), Instant.now());
        if (json == null) {
            return null;
        }
        OAuth2AuthorizationRequest recovered = codec.decode(json);
        if (!state.equals(recovered.getState())) {
            throw new IllegalStateException("OAuth authorization request state mismatch");
        }
        if (consume) {
            addCookie(response, COOKIE_PREFIX + id, "", Duration.ZERO);
        }
        return recovered;
    }

    private static String browserCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration age) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(properties.secureCookie())
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(age)
                        .build()
                        .toString());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
