package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.auth.dto.LoginRequest;
import eightjbbm.keepgo.auth.dto.RefreshResponse;
import eightjbbm.keepgo.auth.refreshtokenhash.RefreshTokenManager;
import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenHashProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class AuthController {

    private final JwtProperties jwtProperties;
    private final AccessTokenManager accessTokenManager;
    private final RefreshTokenManager refreshTokenManager;

    @PostMapping("/api/v1/user/auth-session")
    public void login(
            @RequestBody LoginRequest request
    ) {
        URI location = URI.create(
            "/oauth2/authorization/" + request.provider()
        );
    }

    @DeleteMapping("/api/v1/user/auth-session")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = "refresh-token", required = false) String refreshToken
    ) {
        accessTokenManager.revoke(jwt.getId());

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenManager.revoke(refreshToken);
        }

        String deletedCookie = ResponseCookie
                .from("refresh-token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/user/auth-session")
                .maxAge(Duration.ZERO)
                .build()
                .toString();

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, deletedCookie)
                .build();
    }

    @PostMapping("/api/v1/user/auth-session/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = "refresh-token") String refreshToken
    ) {
        Long memberId = refreshTokenManager.get(refreshToken)
                .orElseThrow()
                .memberId();
        String newAccessToken = accessTokenManager.issue(memberId, List.of());
        String newRefreshToken = refreshTokenManager.rotate(refreshToken);

        String refreshTokenCookie = ResponseCookie
                .from("refresh-token", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/user/auth-session")
                .maxAge(Duration.ofDays(14))
                .build().toString();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(RefreshResponse.from(
                        newAccessToken,
                        "Bearer",
                        Math.toIntExact(jwtProperties.accessTokenTtl().toSeconds())
                ));
    }
}
