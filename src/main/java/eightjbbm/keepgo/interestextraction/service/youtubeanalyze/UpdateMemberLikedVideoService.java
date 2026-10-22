package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.repository.memberlike.MemberLikedVideo;
import eightjbbm.keepgo.interestextraction.repository.memberlike.MemberLikedVideoRepository;
import eightjbbm.keepgo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMemberLikedVideoService {

    private final MemberRepository memberRepository;
    private final MemberLikedVideoRepository memberLikedVideoRepository;
    private final UpdateOutingCollectionPrivatesService updateOutingCollectionPrivatesService;
    /// 회원의 좋아요 이력 최신화
    ///
    @Transactional
    public void updateMemberLikedVideos(Long memberId, List<Video> likedVideosYoutubeResponse) {
        // 회원의 좋아요 이력을 불러온다.
        List<String> memberLikedVideoIds = memberLikedVideoRepository.findExistingVideos(
                memberId,
                likedVideosYoutubeResponse.stream()
                        .map(Video::getId)
                        .toList());
        // 유튜브 영상 조회 API 결과 반환된 회원과 연결된 유튜브 계정의 좋아요 표시한 동영상 목록과 대조하여 아직 좋아요 이력에 반영되지 못한 영상들의 목록을 추출한다.
        List<Video> unupdatedVideos = likedVideosYoutubeResponse.stream()
                .filter(video -> !memberLikedVideoIds.contains(video.getId()))
                .toList();
        log.info("updateMemberLikedVideos: unupdatedVideos.size() = " + unupdatedVideos.size());
        if (unupdatedVideos.isEmpty()) return;
        // 좋아요 이력을 최신화한다.
        updateOutingCollectionPrivatesService.updateOutingCollectionPrivates(memberId, unupdatedVideos);
        memberLikedVideoRepository.saveAll(
                unupdatedVideos.stream()
                        .map(video ->
                                new MemberLikedVideo(
                                        video.getId(),
                                        memberRepository.getReferenceById(memberId)))
                        .toList());
    }
}
