package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import eightjbbm.keepgo.util.file.FileRepository;
import eightjbbm.keepgo.util.file.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final GoogleApiClient googleApiClient;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    /// 회원 정보 수정 API
    /// @param command {@link UpdateMemberInfoCommand}
    /// @return {@link UpdateMemberInfoResult}
    @Transactional
    public UpdateMemberInfoResult updateMemberInfo(UpdateMemberInfoCommand command) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        member.updateNickname(command.nickname());

        String profileImagePath = FileUtil.extractPathFromUrl(command.profileImagePath());

        fileRepository.findByStoragePath(profileImagePath).ifPresent(member::updateProfileImage);
        return UpdateMemberInfoResult.from(member);
    }

    /// 회원 정보 조회 API
    ///
    /// 상태: 구현 완료
    /// @param command {@link GetMemberInfoCommand}
    /// @result {@link GetMemberInfoResult}
    public GetMemberInfoResult getMemberInfo(GetMemberInfoCommand command) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        return GetMemberInfoResult.from(member, oAuthAccount);
    }

    /// 좋아요한 동영상 목록 동기화 API
    ///
    /// 회원의 좋아요한 동영상 재생목록 ID를 가져오는 것은 회원가입 때 진행해야 됨.
    /// @param command {@link SynchronizeYoutubeLikeVideosCommand}
    public void synchronizeYoutubeLikeVideos(SynchronizeYoutubeLikeVideosCommand command) {
        Member member = memberRepository.findById(command.memberId()).orElseThrow();
        var oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        var client = oAuth2AuthorizedClientService.loadAuthorizedClient("google", oAuthAccount.getName());
        if (client == null) {
            throw new IllegalStateException("Google OAuth 인증 정보가 없습니다.");
        }
        String oAuth2AccessToken = client.getAccessToken().getTokenValue();
        var likedVideos = googleApiClient.getLikedVideos(oAuth2AccessToken);
        log.info("좋아요한 동영상 조회: count={}", likedVideos.items().size());
    }
}
