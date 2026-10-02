package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        // AI의 place_id는 후보로 보낸 OutingGuide id다. 보낸 후보 안에서 찾는다.
        Map<String, OutingGuide> candidates = collection.stream()
                .map(OutingCollectionPrivate::getOutingGuide)
                .collect(Collectors.toMap(guide -> guide.getId().toString(), Function.identity()));

        return RequestRecommendationResult.from(
                slot, recommendCourseResponse,
                place -> candidates.get(place.placeId())
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
