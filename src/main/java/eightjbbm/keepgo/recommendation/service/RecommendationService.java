package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.repository.OutingGuideRepository;
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
    private final OutingGuideRepository outingGuideRepository;
    private final QueryCacheService queryCacheService;
    private final SlotCacheService slotCacheService;
    private final RecommendationJobCacheService recommendationJobCacheService;

    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        Long memberId = command.memberId();
        String query = queryCacheService.getQuery(memberId);
        SlotValue slot = slotCacheService.getSlot(memberId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "추천 조건이 없습니다. 의도 카드를 먼저 저장해 주세요."
        ));
        List<OutingGuide> candidates = candidatePlaces();
        List<OutingCollectionPrivate> history = recentSavedPlaces(memberId);
        recommendationJobCacheService.createJob(memberId, 0L);

        var recommendCourseResponse = aiServerApiClient.recommendCourse(RecommendCourseRequest.from(
                query, candidates, history, slot
        ));

        recommendationJobCacheService.completeJob(command.memberId());

        // AI의 place_id는 후보로 보낸 OutingGuide id다. 보낸 후보 안에서 찾는다.
        Map<String, OutingGuide> candidateById = candidates.stream()
                .collect(Collectors.toMap(guide -> guide.getId().toString(), Function.identity()));

        return RequestRecommendationResult.from(
                slot, recommendCourseResponse,
                place -> candidateById.get(place.placeId())
        );
    }

    /// 코스를 짤 후보 장소. 원래는 출발지 반경으로 1차 필터링해야 하지만, 장소 좌표를 아직 모으지 못해
    /// 이름이 있는 장소를 최근 등록 순으로 AI 한도({@link RecommendCourseRequest#MAX_PLACES})까지 보낸다.
    ///
    /// 영상 분석이 장소명을 찾지 못한 장소는 이름 없이 저장돼 있다. AI가 장소명을 필수로 받아
    /// 하나만 섞여도 요청 전체가 422가 되고, 이름 없는 장소는 코스로 보여줄 수도 없으므로 뺀다.
    private List<OutingGuide> candidatePlaces() {
        return outingGuideRepository.findTop50ByNameIsNotNullOrderByIdDesc().stream()
                .filter(guide -> !guide.getName().isBlank())
                .toList();
    }

    /// 취향 계산용 이력. 회원이 저장한 장소를 최근 순으로, 같은 장소는 한 번만, AI 한도까지 고른다.
    private List<OutingCollectionPrivate> recentSavedPlaces(Long memberId) {
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
