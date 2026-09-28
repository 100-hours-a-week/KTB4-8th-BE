package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.repository.OutingEventRepository;
import eightjbbm.keepgo.recommendation.repository.OutingPlaceRepository;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.file.File;
import eightjbbm.keepgo.util.file.FileRepository;
import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import eightjbbm.keepgo.util.dto.AnalyzeVideoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final OutingPlaceRepository outingPlaceRepository;
    private final OutingEventRepository outingEventRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final GoogleApiClient googleApiClient;
    private final AiServerApiClient aiServerApiClient;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    /// 회원 정보 수정 API
    /// @param command {@link UpdateMemberInfoCommand}
    /// @return {@link UpdateMemberInfoResult}
    public UpdateMemberInfoResult updateMemberInfo(
            UpdateMemberInfoCommand command
    ) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        member.updateNickname(command.nickname());
        File profileImage = fileRepository.findByStoragePath(command.profileImagePath()).orElseThrow();
        member.updateProfileImage(profileImage);
        return UpdateMemberInfoResult.from(member);
    }

    /// 회원 정보 조회 API
    ///
    /// 상태: 구현 완료
    /// @param command {@link GetMemberInfoCommand}
    /// @result {@link GetMemberInfoResult}
    public GetMemberInfoResult getMemberInfo(
            GetMemberInfoCommand command
    ) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        return GetMemberInfoResult.from(member, oAuthAccount);
    }

    /// 좋아요한 동영상 목록 동기화 API
    ///
    /// 회원의 좋아요한 동영상 재생목록 ID를 가져오는 것은 회원가입 때 진행해야 됨.
    /// @param command {@link SynchronizeYoutubeLikeVideosCommand}
    public void synchronizeYoutubeLikeVideos(
            SynchronizeYoutubeLikeVideosCommand command,
            Authentication authentication
    ) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        if (!(authentication instanceof OAuth2AuthenticationToken oAuthToken)) {
            throw new IllegalStateException("OAuth2 인증 정보가 아님");
        }

        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
                "google",
                authentication.getName()
        );

        if (authorizedClient == null) {
            throw new IllegalStateException("OAuth2AuthorizedClient를 찾지 못했음");
        }

        OAuth2AccessToken oAuth2AccessToken = authorizedClient.getAccessToken();

        if (oAuth2AccessToken == null) {
            throw new IllegalStateException("Google Access Token이 없음");
        }

        List<String> urls = googleApiClient.retrieveLikedVideos(member.getLikedVideosPlaylistId(), oAuth2AccessToken.getTokenValue()).getVideoIds();
        for (String url: urls) {
            AnalyzeVideoResponse response = aiServerApiClient.analyzeVideo(url);
            OutingGuide guide;
            if (isPlaceOrEvent(response.getCategory())) {
                guide = outingPlaceRepository.findByName(response.getName()).orElseGet(
                        () -> outingPlaceRepository.save(new OutingPlace(
                                response.getCategory(),
                                response.getName(),
                                response.getSummary()
                        ))
                );
            } else {
                guide = outingEventRepository.findByName(response.getName()).orElseGet(
                        () -> outingEventRepository.save(new OutingEvent(
                                response.getCategory(),
                                response.getName(),
                                response.getSummary(),
                                null,
                                response.data().eventStartDate(), 
                                response.data().eventEndDate()
                        ))
                );
            }
            outingCollectionPrivateRepository.save(new OutingCollectionPrivate(member, guide));
        }
    }

    private boolean isPlaceOrEvent(String category) {
        return category.equals("팝업") || category.equals("전시");
    }
}
