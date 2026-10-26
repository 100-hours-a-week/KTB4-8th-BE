package eightjbbm.keepgo.interestextraction.service.youtubeanalyzev3;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.interestextraction.service.YoutubeApiServiceV2;
import eightjbbm.keepgo.interestextraction.service.InterestExtractionService;
import eightjbbm.keepgo.interestextraction.service.youtubeanalyze.GuideKey;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.sandbox.OutingGuideFactory;
import eightjbbm.keepgo.sandbox.OutingGuideRepository;
import eightjbbm.keepgo.sandbox.aiv2.AiServerService;
import eightjbbm.keepgo.sandbox.aiv2.AnalyzeVideoResponseV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestExtractionServiceV3 {

    private final YoutubeApiServiceV2 youtubeApiService;
    private final AiServerService aiServerApiService;
    private final OutingGuideRepository outingGuideRepository;
    private final MemberRepository memberRepository;
    private final OutingWishlist outingWishlist;

    @Transactional
    public void syncWishlistFromYoutube(Long memberId) {
        List<Video> memberLikedVideos = retrieveMemberLikedVideosFromYoutube(memberId);
        List<OutingGuide> analyzedResults = analyzeYoutubeVideos(memberLikedVideos);
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

    /// 3분이 넘어가거나, 분석이 불가능한 영상을 분석 대상에서 제외한다.
    ///
    private List<Video> validateYoutubeVideos(List<Video> videos) {
        return videos.stream()
                .filter(video -> video.getStatus() != null)
                .filter(video -> "uploaded".equals(video.getStatus().getUploadStatus()))
                .filter(video -> "public".equals(video.getStatus().getPrivacyStatus()))
                .filter(video -> video.getContentDetails() != null)
                .filter(video -> video.getContentDetails().getDuration() != null)
                .filter(video -> java.time.Duration.parse(video.getContentDetails().getDuration())
                        .compareTo(java.time.Duration.ofMinutes(3)) <= 0)
                .toList();
    }

    private List<OutingGuide> analyzeYoutubeVideos(List<Video> videos) {
        // 유효성 검사
        List<String> validatedVideoIds = validateYoutubeVideos(videos).stream().map(Video::getId).toList();

        // AI 분석
        List<OutingGuide> analyzedResults = new ArrayList<>();
        for (int i = 0; i < validatedVideoIds.size(); i += 10) {
            var response = aiServerApiService.analyzeVideoV2(
                    validatedVideoIds.subList(i, Math.min(i + 10, validatedVideoIds.size())));
            if (response instanceof AnalyzeVideoResponseV2.Success success) {
                for (var result: success.data().results()) {
                    switch (result.status()) {
                        case "success" -> analyzedResults.add(OutingGuideFactory.createOutingGuide(result));
                        case "not_found", "failed" -> {}
                        default -> throw new IllegalStateException();
                    }
                }
            }
        }

        return persistAnalyzedResults(analyzedResults);
    }

    private List<OutingGuide> persistAnalyzedResults(List<OutingGuide> analyzedResults) {
        Map<GuideKey, OutingGuide> allByAddresses = outingGuideRepository
                .findAllByAddresses(
                        analyzedResults.stream()
                                .map(OutingGuide::getAddress)
                                .distinct()
                                .toList()).stream()
                .collect(Collectors.toMap(
                        outingGuide -> new GuideKey(outingGuide.getName(), outingGuide.getAddress()),
                        outingGuide -> outingGuide,
                        (existing, duplicate) -> existing));

        Map<GuideKey, OutingGuide> uniqueGuides = new LinkedHashMap<>();

        List<OutingGuide> persistedGuides = new ArrayList<>();
        List<OutingGuide> unembeddedGuides = new ArrayList<>();
        for (OutingGuide guide: analyzedResults) {
            GuideKey key = new GuideKey(guide.getName(), guide.getAddress());
            if (!uniqueGuides.containsKey(key)) {
                uniqueGuides.put(key, guide);
                if (!allByAddresses.containsKey(key)) {
                    OutingGuide persistedGuide = outingGuideRepository.save(guide);
                    persistedGuides.add(persistedGuide);
                    unembeddedGuides.add(persistedGuide);
                } else {
                    persistedGuides.add(allByAddresses.get(key));
                }
            }
        }

        for (int i = 0; i < unembeddedGuides.size(); i += 100) {
            aiServerApiService.embedPlaces(unembeddedGuides.subList(i, Math.min(i + 100, unembeddedGuides.size())));
        }

        return persistedGuides;
    }

    private void addOutingGuidesToOutingWishlist(Long memberId, List<OutingGuide> analyzedResults) {
        List<OutingGuide> wishlistedOutingGuides = outingWishlist
                .findAllWishlistedOutingGuides(memberId, analyzedResults);

        Member member = memberRepository.getReferenceById(memberId);
        for (OutingGuide guide: analyzedResults) {
            if (!wishlistedOutingGuides.contains(guide)) {
                outingWishlist.save(OutingWishlistEntry.from(member, guide));
            }
        }
    }
}
