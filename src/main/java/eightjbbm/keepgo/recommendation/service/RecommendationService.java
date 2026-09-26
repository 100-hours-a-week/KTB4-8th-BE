package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.member.entity.OutingCollectionPrivate;
import eightjbbm.keepgo.member.repository.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.dto.*;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.client.AiServerClient;
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

    private final AiServerClient aiServerClient;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final QueryCacheService queryCacheService;
    private final SlotCacheService slotCacheService;
    private final RecommendationJobCacheService recommendationJobCacheService;

    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        String query = queryCacheService.getQuery(command.userId());
        List<OutingCollectionPrivate> collection = outingCollectionPrivateRepository.findAll(); //location으로 1차 필터링
        SlotValue slot = slotCacheService.read(command.userId());
        recommendationJobCacheService.createJob(command.userId(), 0L);

        var recommendCourseResponse = aiServerClient.recommendCourse(RecommendCourseRequest.from(
                query, collection, slot
        ));

        recommendationJobCacheService.completeJob(command.userId());

        return RequestRecommendationResult.from(
                slot, recommendCourseResponse,
                (k -> (OutingPlace) outingCollectionPrivateRepository
                        .findById(Long.valueOf(k.placeId()))
                        .orElseThrow()
                        .getOutingGuide())
        );
    }

    public void stopRecommendation(StopRecommendationCommand command) {
        Long jobId = recommendationJobCacheService.getJobId(command.userId());
        aiServerClient.stopRecommendation(jobId);
    }
}
