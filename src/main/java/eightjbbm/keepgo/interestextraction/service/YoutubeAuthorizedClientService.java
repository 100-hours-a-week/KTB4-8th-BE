package eightjbbm.keepgo.interestextraction.service;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YoutubeAuthorizedClientService {
    public static final String GOOGLE_CLIENT_YOUTUBE = "google";
    private final OAuth2AuthorizedClientManager youtubeAuthorizedClientManager;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final MemberRepository memberRepository;

    public OAuth2AccessToken getAccessToken(Long memberId) {
        OAuthAccount account = getGoogleAccount(memberId);

        OAuth2AuthorizedClient stored =
                oAuth2AuthorizedClientService.loadAuthorizedClient(
                        GOOGLE_CLIENT_YOUTUBE,
                        account.getSubject()
                );

        if (stored == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "유튜브 계정이 연동되어 있지 않습니다."
            );
        }

        var principal = UsernamePasswordAuthenticationToken.authenticated(
                account.getSubject(),
                null,
                List.of());

        OAuth2AuthorizedClient authorized;

        try {
            authorized = youtubeAuthorizedClientManager.authorize(
                    OAuth2AuthorizeRequest.withAuthorizedClient(stored)
                            .principal(principal)
                            .build()
            );
        } catch (OAuth2AuthorizationException error) {
            if ("invalid_grant".equals(error.getError().getErrorCode())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "유튜브 인증이 만료되었습니다. 다시 연동해 주세요.",
                        error);
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google 인증 토큰을 갱신하지 못했습니다.",
                    error);
        }

        if (authorized == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "유튜브 계정을 다시 연동해 주세요.");
        }

        OAuth2AccessToken token = authorized.getAccessToken();

        // refresh token이 없으면 manager가 만료된 기존 client를 반환할 수 있다.
        if (token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(
                Instant.now().plusSeconds(30)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "유튜브 인증을 갱신할 수 없습니다. 다시 연동해 주세요."
            );
        }

        if (!token.getScopes().contains(
                "https://www.googleapis.com/auth/youtube.readonly"
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "유튜브 조회 권한이 없습니다. 다시 연동해 주세요."
            );
        }

        return token;
    }


    public void removeAuthorizedClient(Long memberId) {
        OAuthAccount account = getGoogleAccount(memberId);

        oAuth2AuthorizedClientService.removeAuthorizedClient(
                GOOGLE_CLIENT_YOUTUBE,
                account.getSubject()
        );
    }

    private OAuthAccount getGoogleAccount(Long memberId) {
        return oAuthAccountRepository.findByMember(
                memberRepository.getReferenceById(memberId)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Google 로그인 계정을 찾을 수 없습니다."
        ));
    }
}
