package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationJobCacheService {
    private final RecommendationJobCacheRepository recommendationJobCacheRepository;

    public void createJob(Long memberId, Long jobId) {
        recommendationJobCacheRepository.create(memberId, jobId);
    }

    public Long getJobId(Long memberId) {
        return recommendationJobCacheRepository.read(memberId).orElseThrow();
    }

    public void completeJob(Long memberId) {
        recommendationJobCacheRepository.delete(memberId);
    }
}
