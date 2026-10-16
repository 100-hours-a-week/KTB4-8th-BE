package eightjbbm.keepgo.interestextraction.service;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class YoutubeAuthorizedClientService {
    private static final String GOOGLE_CLIENT_YOUTUBE = "google";
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final MemberRepository memberRepository;

    public OAuth2AccessToken getAccessToken(Long memberId) {
        Member member = memberRepository.getReferenceById(memberId);
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        String principalName = oAuthAccount.getSubject();
        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
                GOOGLE_CLIENT_YOUTUBE,
                principalName
        );
        if (authorizedClient == null) {
            throw new RuntimeException();
        }
        return authorizedClient.getAccessToken();
    }
}
