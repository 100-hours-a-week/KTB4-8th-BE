package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.dto.LoginResponse;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.client.YoutubeApiClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;
    private final YoutubeApiClient youtubeApiClient;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OidcUser oidcUser = (OidcUser) authentication;

        Member member = oAuthAccountRepository.findByIssSub(
                    oidcUser.getIssuer().toString(),
                    oidcUser.getSubject()
                )
                .orElseGet(
                        () -> {
                            Member newMember = memberRepository.save(
                                    Member.create(
                                            oidcUser.getNickName()
                                    )
                            );
                            newMember.setLikedVideosPlaylistId(youtubeApiClient.retrieveLikesPlaylistId(newMember.getId()).getLikesPlaylistId());
                            return oAuthAccountRepository.save(
                                    OAuthAccount.create(
                                            newMember,
                                            oidcUser.getIssuer().toString(),
                                            oidcUser.getSubject()
                                    )
                            );
                        }
                )
                .getMember();

        String accessToken = tokenProvider.issueAccessToken(member);
        String refreshToken = tokenProvider.issueAndRotateRefreshToken(member);

        ResponseCookie cookie = ResponseCookie
                .from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/user/auth-session/refresh")
                .maxAge(Duration.ofDays(14))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        objectMapper.writeValue(
                response.getOutputStream(),
                ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(LoginResponse.from(
                                accessToken,
                                "Bearer",
                                3600
                        ))
        );
    }
}
