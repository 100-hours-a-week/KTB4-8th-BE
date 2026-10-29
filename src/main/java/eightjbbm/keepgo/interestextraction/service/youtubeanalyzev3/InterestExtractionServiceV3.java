package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import eightjbbm.keepgo.interestextraction.service.MemberLikedVideoReader;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.YoutubeVideoAnalyzer;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistSyncService;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestExtractionServiceV3 {

    private final MemberLikedVideoReader memberLikedVideoReader;
    private final YoutubeVideoAnalyzer youtubeVideoAnalyzer;
    private final OutingWishlistSyncService outingWishlistSyncService;

    @Transactional
    public void syncWishlistFromYoutubePlain(Long memberId) {
        List<OutingGuide> analyzedResults = youtubeVideoAnalyzer.analyze(memberLikedVideoReader.readAll(memberId));
        outingWishlistSyncService.sync(memberId, analyzedResults);
    }
}
