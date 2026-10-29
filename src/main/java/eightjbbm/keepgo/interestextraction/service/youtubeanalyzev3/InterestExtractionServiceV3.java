package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.service.YoutubeApiServiceV2;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestExtractionServiceV3 {

    private final YoutubeApiServiceV2 youtubeApiService;
    private final MemberRepository memberRepository;
    private final OutingWishlist outingWishlist;
    private final YoutubeVideoAnalyzer youtubeVideoAnalyzer;

    @Transactional
    public void syncWishlistFromYoutubePlain(Long memberId) {
        List<Video> memberLikedVideos = retrieveMemberLikedVideosFromYoutube(memberId);
        List<OutingGuide> analyzedResults = youtubeVideoAnalyzer.analyze(memberLikedVideos);
        addOutingGuidesToOutingWishlist(memberId, analyzedResults);
    }

    private List<Video> retrieveMemberLikedVideosFromYoutube(Long memberId) {
        String pageToken = null;
        List<Video> videoLists = new ArrayList<>();
        do {
            // pageToken = null인 경우 pageToken을 요청에 포함하지 않는다 => 첫 페이지 조회
            var response = youtubeApiService.getVideoLists(memberId, pageToken);
            videoLists.addAll(response.getItems());

            pageToken = response.getNextPageToken();
        } while (pageToken != null);
        return videoLists;
    }

    private void addOutingGuidesToOutingWishlist(Long memberId, List<OutingGuide> analyzedResults) {
        List<OutingGuide> wishlistedOutingGuides =
                outingWishlist.findAllWishlistedOutingGuides(memberId, analyzedResults);

        Member member = memberRepository.getReferenceById(memberId);
        for (OutingGuide guide : analyzedResults) {
            if (!wishlistedOutingGuides.contains(guide)) {
                outingWishlist.save(OutingWishlistEntry.from(member, guide));
            }
        }
    }
}
