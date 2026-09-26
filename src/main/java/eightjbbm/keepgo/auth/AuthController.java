package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.dto.LoginRequest;
import eightjbbm.keepgo.auth.dto.RefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AccessTokenManager accessTokenManager;
    private final RefreshTokenManager refreshTokenManager;

    @PostMapping("/user/auth-session")
    public ResponseEntity<Void> login(
            LoginRequest request
    ) {
        URI location = URI.create(
            "/oauth2/authorization/" + request.provider()
        );

        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(location)
                .build();
    }

    @DeleteMapping("/user/auth-session")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = "refresh-token") String refreshToken
    ) {
        accessTokenManager.revoke(jwt.getId());
        refreshTokenManager.revoke(
                Long.valueOf(jwt.getSubject()),
                refreshToken
        );

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @PostMapping("/user/auth-session/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = "refresh-token") String refreshToken
    ) {
        String newAccessToken = accessTokenManager.issue(0L, List.of());
        String newRefreshToken = refreshTokenManager.rotate(refreshToken);

        ResponseCookie cookie = ResponseCookie
                .from("refresh_token", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/user/auth-session")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(RefreshResponse.from(
                        newAccessToken,
                        "Bearer",
                        3600
                ));
    }
}
