package eightjbbm.keepgo.interestextraction.service.youtubeanalyze;

import com.google.api.services.youtube.model.Video;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateOutingCollectionPrivatesService {

    private final MemberRepository memberRepository;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final AnalyzeVideosService analyzeVideosService;
    /// 회원의 여행 위시리스트 최신화
    ///
    @Transactional
    public void updateOutingCollectionPrivates(Long memberId, List<Video> unupdatedVideos) {
        // 아직 좋아요 이력에 반영되지 못한 영상들의 목록을 분석한다.
        // 블로킹 지점 (동기 논블로킹 필요?)
        List<OutingGuide> outingGuidesExtracted = analyzeVideosService.analyzeVideos(unupdatedVideos);
        if (outingGuidesExtracted.isEmpty()) return;
        // 분석 결과 추출된 장소와 행사를 기존 여행 위시리스트와 대조하여 최신화가 필요한 부분을 파악한다.
        // 추출된 장소와 행사 중 이미 여행 위시리스트에 등록되어있는 것들
        Set<OutingGuide> wishlistedOutingGuides = outingCollectionPrivateRepository
                .findAllByMemberIdAndOutingGuides(
                        memberId,
                        new HashSet<>(outingGuidesExtracted)).stream()
                .map(OutingCollectionPrivate::getOutingGuide)
                .collect(Collectors.toSet());
        // 여행 위시리스트에 추가하여 미처 반영되지 못한 부분을 추가하여 최신화한다.
        outingCollectionPrivateRepository.saveAll(
                outingGuidesExtracted.stream()
                        .filter(outingGuide -> !wishlistedOutingGuides.contains(outingGuide))
                        .map(outingGuide ->
                                OutingCollectionPrivate.from(
                                        memberRepository.getReferenceById(memberId),
                                        outingGuide))
                        .toList());
    }
}
