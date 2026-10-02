package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.cache.recommendationjob.RecommendationJobCacheService;
import eightjbbm.keepgo.util.cache.query.QueryCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.RecommendCourseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiServerApiClient aiServerApiClient;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final QueryCacheService queryCacheService;
    private final SlotCacheService slotCacheService;
    private final RecommendationJobCacheService recommendationJobCacheService;

    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        Long memberId = command.memberId();
        String query = queryCacheService.getQuery(memberId);
        List<OutingCollectionPrivate> collection = recentDistinctPlaces(memberId);
        SlotValue slot = slotCacheService.getSlot(memberId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "추천 조건이 없습니다. 의도 카드를 먼저 저장해 주세요."
        ));
        recommendationJobCacheService.createJob(memberId, 0L);

        var recommendCourseResponse = aiServerApiClient.recommendCourse(RecommendCourseRequest.from(
                query, collection, slot
        ));

        recommendationJobCacheService.completeJob(command.memberId());

        return RequestRecommendationResult.from(
                slot, recommendCourseResponse,
                (k -> (OutingPlace) outingCollectionPrivateRepository
                        .findById(Long.valueOf(k.placeId()))
                        .orElseThrow()
                        .getOutingGuide())
        );
    }

    /// 회원이 저장한 장소를 최근 순으로, 같은 장소는 한 번만, AI 한도({@link RecommendCourseRequest#MAX_PLACES})까지 고른다.
    private List<OutingCollectionPrivate> recentDistinctPlaces(Long memberId) {
        Set<Long> seenGuideIds = new HashSet<>();
        return outingCollectionPrivateRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
                .filter(saved -> saved.getOutingGuide() != null)
                .filter(saved -> seenGuideIds.add(saved.getOutingGuide().getId()))
                .limit(RecommendCourseRequest.MAX_PLACES)
                .toList();
    }

    public void stopRecommendation(StopRecommendationCommand command) {
        Long jobId = recommendationJobCacheService.getJobId(command.memberId());
        aiServerApiClient.stopRecommendation(jobId);
    }
}
