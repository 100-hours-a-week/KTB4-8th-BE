package eightjbbm.keepgo.recommendation.repository;

import java.util.Optional;

public interface RecommendationJobStore {

    enum State {
        RUNNING,
        CANCELLED,
        FINISHED
    }

    record Job(String id, State state) {}

    boolean start(Long memberId, String jobId);

    Optional<Job> find(Long memberId);

    boolean finish(Long memberId, String jobId, State state);

    void remove(Long memberId, String jobId);
}
