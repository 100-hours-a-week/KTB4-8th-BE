package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import eightjbbm.keepgo.auth.authorizationrequest.AuthorizationRequestProperties;
import eightjbbm.keepgo.auth.authorizationrequest.AuthorizationRequestStorageException;
import eightjbbm.keepgo.auth.authorizationrequest.OAuthSentryReporter;
import eightjbbm.keepgo.auth.authorizationrequest.SharedAuthorizationRequestRepository;
import eightjbbm.keepgo.interestextraction.service.*;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterestExtractionService {

    private static final String PENDING_YOUTUBE_LINK =
            "eightjbbm.keepgo.interestextraction.service.youtubeanalyze.InterestExtractionServiceV2.pendingYoutubeLink";

    private final YoutubeAuthorizedClientService youtubeAuthorizedClientService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;

    private final SharedAuthorizationRequestRepository authorizationRequests;
    private final OAuthSentryReporter sentryReporter;
    private final AuthorizationRequestProperties authorizationRequestProperties;

    /// 유튜브 연동 API
    ///
    public String linkYoutubeAccount(Long memberId, HttpServletRequest request, HttpServletResponse response) {
        OAuthAccount account = getGoogleAccount(memberId);

        var resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");

        resolver.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(parameters -> {
            parameters.put("login_hint", account.getSubject());
            parameters.put("access_type", "offline");
        }));

        OAuth2AuthorizationRequest authorizationRequest =
                resolver.resolve(request, YoutubeAuthorizedClientService.GOOGLE_CLIENT_YOUTUBE);

        if (authorizationRequest == null) {
            throw new IllegalStateException("YouTube 조회용 OAuth 클라이언트가 설정되지 않았습니다.");
        }

        // 콜백에서 Spring Security가 인증 요청과 state를 검증한다.
        try {
            authorizationRequests.saveAuthorizationRequest(authorizationRequest, request, response);
        } catch (AuthorizationRequestStorageException exception) {
            sentryReporter.report("start", authorizationRequestProperties.provider());
            throw exception;
        }

        // 같은 브라우저 세션에서는 가장 최근 연동 요청을 사용한다.
        request.getSession()
                .setAttribute(
                        PENDING_YOUTUBE_LINK,
                        new PendingYoutubeLink(
                                authorizationRequest.getState(),
                                account.getIssuer(),
                                account.getSubject(),
                                Instant.now()));

        return authorizationRequest.getAuthorizationRequestUri();
    }

    /// 유튜브 연동 정보 조회 API
    ///
    public GetYoutubeConnectionResult getYoutubeConnection(GetYoutubeConnectionCommand command) {
        return new GetYoutubeConnectionResult(null, null, null, 0, 0);
    }

    /// 유튜브 연동 해제 API
    ///
    public void unlinkYoutubeAccount(UnlinkYoutubeAccountCommand command) {
        youtubeAuthorizedClientService.removeAuthorizedClient(command.memberId());
    }

    /// 연결된 서비스 목록 조회 API
    ///
    public GetConnectedServiceListResult getConnectedServiceList(GetConnectedServiceListCommand command) {
        return new GetConnectedServiceListResult(new GetConnectedServiceListResult.ServiceConnected("YouTube", null));
    }

    private OAuthAccount getGoogleAccount(Long memberId) {
        return oauthAccountRepository
                .findByMember(memberRepository.getReferenceById(memberId))
                .orElseThrow(InterestExtractionService::googleAccountNotFound);
    }

    private static NotFoundException googleAccountNotFound() {
        var exception = new NotFoundException();
        exception.setDetail("Google 로그인 계정을 찾을 수 없습니다.");
        return exception;
    }

    private record PendingYoutubeLink(String state, String issuer, String subject, Instant createdAt)
            implements Serializable {}
}
