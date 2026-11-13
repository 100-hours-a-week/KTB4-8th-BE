package eightjbbm.keepgo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eightjbbm.keepgo.auth.accesstokenblacklist.AccessTokenManager;
import eightjbbm.keepgo.auth.refreshtokenhash.RefreshTokenManager;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@DisplayName("OIDC 로그인 성공 핸들러")
class OidcLoginSuccessHandlerTest {

    private static final String ACCESS_TOKEN = "header.payload.signature";

    private final OAuthAccountRepository oAuthAccountRepository = mock(OAuthAccountRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final AccessTokenManager accessTokenManager = mock(AccessTokenManager.class);
    private final RefreshTokenManager refreshTokenManager = mock(RefreshTokenManager.class);
    private final Member member = mock(Member.class);
    private final OidcUser oidcUser = mock(OidcUser.class);
    private final OAuth2AuthenticationToken authentication =
            new OAuth2AuthenticationToken(oidcUser, List.of(), "google");

    @BeforeEach
    void setUp() throws Exception {
        when(member.getId()).thenReturn(7L);
        when(oidcUser.getIssuer())
                .thenReturn(URI.create("https://accounts.google.com").toURL());
        when(oidcUser.getSubject()).thenReturn("subject");
        when(oidcUser.getClaims()).thenReturn(Map.of("name", "홍길동"));
        when(accessTokenManager.issue(7L, List.of())).thenReturn(ACCESS_TOKEN);
        when(refreshTokenManager.issue(7L)).thenReturn("refresh-token");
    }

    @Test
    @DisplayName("신규 회원은 Access Token을 쿼리가 아닌 Fragment로 받고 initialLogin이 true다")
    void newMemberReceivesTokenInFragmentOnly() throws Exception {
        when(oAuthAccountRepository.findByIssuerAndSubject(any(), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenReturn(member);
        when(oAuthAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletResponse response = login("http://localhost:3000");

        UriComponents location =
                UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build();
        assertThat(location.getQuery()).isNull();
        assertThat(location.getPath()).isNullOrEmpty();
        assertThat(parseFragment(location.getFragment()))
                .containsEntry("accessToken", ACCESS_TOKEN)
                .containsEntry("expiresIn", "1800")
                .containsEntry("initialLogin", "true");
    }

    @Test
    @DisplayName("기존 회원은 initialLogin이 false이고 목적지 경로와 기존 쿼리를 보존한다")
    void existingMemberKeepsDestinationPathAndQuery() throws Exception {
        when(oAuthAccountRepository.findByIssuerAndSubject(any(), any()))
                .thenReturn(Optional.of(OAuthAccount.create(member, "a@b.c", "https://accounts.google.com", "s", "n")));

        MockHttpServletResponse response = login("http://localhost:3000/login/callback?from=home");

        UriComponents location =
                UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build();
        assertThat(location.getPath()).isEqualTo("/login/callback");
        assertThat(location.getQuery()).isEqualTo("from=home");
        assertThat(parseFragment(location.getFragment()))
                .containsEntry("accessToken", ACCESS_TOKEN)
                .containsEntry("expiresIn", "1800")
                .containsEntry("initialLogin", "false");
    }

    @Test
    @DisplayName("Refresh Token 쿠키의 속성을 유지한다")
    void refreshTokenCookieAttributesAreKept() throws Exception {
        when(oAuthAccountRepository.findByIssuerAndSubject(any(), any()))
                .thenReturn(Optional.of(OAuthAccount.create(member, "a@b.c", "https://accounts.google.com", "s", "n")));

        MockHttpServletResponse response = login("http://localhost:3000");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh-token=refresh-token")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/user/auth-session")
                .contains("Max-Age=1209600");
    }

    private MockHttpServletResponse login(String redirectionAddress) throws Exception {
        var handler = new OidcLoginSuccessHandler(
                oAuthAccountRepository,
                memberRepository,
                accessTokenManager,
                refreshTokenManager,
                new LoginProperties(redirectionAddress),
                mock(OAuth2AuthorizedClientService.class));
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response;
    }

    private static Map<String, String> parseFragment(String fragment) {
        return UriComponentsBuilder.fromUri(URI.create("?" + fragment))
                .build()
                .getQueryParams()
                .toSingleValueMap();
    }
}
