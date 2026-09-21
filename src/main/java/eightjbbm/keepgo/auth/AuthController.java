package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.dto.LoginRequest;
import eightjbbm.keepgo.auth.dto.LoginResponse;
import eightjbbm.keepgo.auth.dto.LogoutResponse;
import eightjbbm.keepgo.auth.dto.RefreshResponse;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    @PostMapping("/user/auth-session")
    public ResponseEntity<Void> login(LoginRequest request) {
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
            @AuthenticationPrincipal Jwt jwt
    ) {
        accessTokenBlacklistRepository.save(new AccessTokenBlacklist(jwt.getTokenValue()));

        Long userId = Long.valueOf(jwt.getSubject());
        Member member = memberRepository.findById(userId).orElseThrow();
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        oAuthAccount.invalidateRefreshToken();

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @PostMapping("/user/auth-session/refresh")
    public ResponseEntity<Void> refresh(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue("refresh_token") String refreshToken
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        Member member = memberRepository.findById(userId).orElseThrow();
        tokenProvider.validateRefreshToken(member, refreshToken);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
