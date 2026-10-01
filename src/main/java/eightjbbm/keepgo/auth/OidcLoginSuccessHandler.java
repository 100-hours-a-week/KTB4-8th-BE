package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(LoginProperties.class)
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final MemberRepository memberRepository;
    private final AccessTokenManager accessTokenManager;
    private final RefreshTokenManager refreshTokenManager;
    private final LoginProperties loginProperties;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        if (!(authentication instanceof OAuth2AuthenticationToken oAuthToken)) {
            throw new IllegalStateException("OAuth2 인증 정보가 아님");
        }

        String registrationId = oAuthToken.getAuthorizedClientRegistrationId();
        String principalName = oAuthToken.getName();
        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
                registrationId,
                principalName
        );

        if (authorizedClient == null) {
            throw new IllegalStateException("OAuth2AuthorizedClient를 찾지 못했음");
        }

        OAuth2AccessToken oAuth2AccessToken = authorizedClient.getAccessToken();

        if (oAuth2AccessToken == null) {
            throw new IllegalStateException("Google Access Token이 없음");
        }

        Member member = oAuthAccountRepository.findByIssuerAndSubject(
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
                            return oAuthAccountRepository.save(
                                    OAuthAccount.create(
                                            newMember,
                                            oidcUser.getEmail(),
                                            oidcUser.getIssuer().toString(),
                                            oidcUser.getSubject(),
                                            oidcUser.getName()
                                    )
                            );
                        }
                )
                .getMember();

        String accessToken = accessTokenManager.issue(member.getId(), List.of());
        String refreshToken = refreshTokenManager.issue(member.getId());

        String refreshTokenCookie = ResponseCookie
                .from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/user/auth-session")
                .maxAge(Duration.ofDays(14))
                .build().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie);
        response.sendRedirect(loginProperties.redirectionAddress() + "?accessToken=" + accessToken + "&expiresIn=3600");
    }
}
