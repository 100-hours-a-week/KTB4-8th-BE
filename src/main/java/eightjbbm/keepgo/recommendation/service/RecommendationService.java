package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationCommand;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationResult;
import eightjbbm.keepgo.recommendation.dto.StopRecommendationCommand;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.util.cache.recommendationjob.RecommendationJobCacheService;
import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.client.ai.AiServerApiClient;
import eightjbbm.keepgo.util.dto.RecommendCourseRequest;
import eightjbbm.keepgo.util.dto.RecommendCourseResponse;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiServerApiClient aiServerApiClient;
    private final OutingCollectionPrivateRepository outingCollectionPrivateRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;
    private final RecommendationJobCacheService recommendationJobCacheService;

    public RequestRecommendationResult requestRecommendation(RequestRecommendationCommand command) {
        Long memberId = command.memberId();
        List<OutingCollectionPrivate> collection = outingCollectionPrivateRepository.findAll(); //location으로 1차 필터링
        OutingCriteria outingCriteria = outingCriteriaRepository.findById(memberId).orElseThrow();
        recommendationJobCacheService.createJob(memberId, 0L);

        var recommendCourseResponse = aiServerApiClient
                .recommendCourse(
                        RecommendCourseRequest.from(
                                outingCriteria.getUserPreferenceSummary(),
                                collection,
                                outingCriteria));

        if (recommendCourseResponse instanceof RecommendCourseResponse.Success successResponse) {
            recommendationJobCacheService.completeJob(command.memberId());
            return RequestRecommendationResult.from(
                    outingCriteria,
                    successResponse,
                    (k -> (OutingPlace) outingCollectionPrivateRepository
                            .findById(Long.valueOf(k.placeId()))
                            .orElseThrow()
                            .getOutingGuide())
            );
        } else {
            throw new ServiceUnavailableException();
        }
    }

    public void stopRecommendation(StopRecommendationCommand command) {
        Long jobId = recommendationJobCacheService.getJobId(command.memberId());
        aiServerApiClient.stopRecommendation(jobId);
    }
}
