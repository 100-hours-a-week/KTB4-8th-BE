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
import org.springframework.stereotype.Service;

import java.util.List;

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
        List<OutingCollectionPrivate> collection = outingCollectionPrivateRepository.findAll(); //location으로 1차 필터링
        SlotValue slot = slotCacheService.read(memberId);
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

    public void stopRecommendation(StopRecommendationCommand command) {
        Long jobId = recommendationJobCacheService.getJobId(command.memberId());
        aiServerApiClient.stopRecommendation(jobId);
    }
}
