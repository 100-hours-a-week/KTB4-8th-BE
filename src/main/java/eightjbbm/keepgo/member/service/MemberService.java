package eightjbbm.keepgo.member.service;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.OutingEventRepository;
import eightjbbm.keepgo.recommendation.OutingPlaceRepository;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.client.AiServerClient;
import eightjbbm.keepgo.util.file.File;
import eightjbbm.keepgo.util.file.FileRepository;
import eightjbbm.keepgo.util.client.YoutubeApiClient;
import eightjbbm.keepgo.util.dto.AnalyzeVideoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final OutingPlaceRepository outingPlaceRepository;
    private final OutingEventRepository outingEventRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final AiServerClient aiServerClient;

    /// 회원 정보 수정 API
    /// @param command {@link UpdateMemberInfoCommand}
    /// @return {@link UpdateMemberInfoResult}
    public UpdateMemberInfoResult updateMemberInfo(
            UpdateMemberInfoCommand command
    ) {
        Member member = memberRepository.findById(command.userId()).orElseThrow();
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
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        return GetMemberInfoResult.from(member);
    }

    /// 좋아요한 동영상 목록 동기화 API
    ///
    /// 회원의 좋아요한 동영상 재생목록 ID를 가져오는 것은 회원가입 때 진행해야 됨.
    /// @param command {@link SynchronizeYoutubeLikeVideosCommand}
    public void synchronizeYoutubeLikeVideos(
            SynchronizeYoutubeLikeVideosCommand command
    ) {
        Member member = memberRepository.findById(command.userId()).orElseThrow();
        List<String> urls = youtubeApiClient.retrieveLikedVideos(member.getLikedVideosPlaylistId()).getVideoIds();
        for (String url: urls) {
            AnalyzeVideoResponse response = aiServerClient.analyzeVideo(url);
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
