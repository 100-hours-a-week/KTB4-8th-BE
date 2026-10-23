package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingCollectionPrivateRepository;
import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.recommendation.repository.OutingStopRepository;
import eightjbbm.keepgo.recommendation.repository.RecommendationJobStore;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.exception.*;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class RecommendationServiceV2 {

    private final AiServerRecommendApiService aiServerService;
    private final OutingCriteriaRepository criteriaRepository;
    private final OutingCollectionPrivateRepository collectionRepository;
    private final KakaoPlaceSearch kakaoPlaceSearch;
    private final RecommendationResultMapper resultMapper;
    private final RecommendationJobStore jobs;
    private final Executor executor;
    private final double radiusKm;

    private final ConcurrentMap<String, RecommendationConnection> localSseConnections =
            new ConcurrentHashMap<>();

    public RecommendationServiceV2(
            AiServerRecommendApiService aiServerService,
            OutingCriteriaRepository criteriaRepository,
            KakaoPlaceSearch kakaoPlaceSearch,
            OutingCollectionPrivateRepository collectionRepository,
            RecommendationResultMapper resultMapper,
            RecommendationJobStore jobs,
            @Qualifier("recommendationTaskExecutor") Executor executor,
            @Value("${recommendation.candidate-radius-km:5}") double radiusKm
    ) {
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new IllegalArgumentException("추천 반경은 양수여야 합니다.");
        }

        this.aiServerService = aiServerService;
        this.criteriaRepository = criteriaRepository;
        this.collectionRepository = collectionRepository;
        this.kakaoPlaceSearch = kakaoPlaceSearch;
        this.resultMapper = resultMapper;
        this.jobs = jobs;
        this.executor = executor;
        this.radiusKm = radiusKm;
    }

    public SseEmitter recommendOutingCourse(Long memberId) {
        OutingCriteria criteria = criteriaRepository.findById(memberId)
                .orElseThrow(NotFoundException::new);
        validate(criteria);
        var connection = new RecommendationConnection(memberId);
        if (!jobs.start(memberId, connection.jobId)) {
            throw new ConflictException();
        }
        try {
            synchronized (connection) {
                connection.emitter.onCompletion(() -> cleanup(connection));
                connection.emitter.onError(error -> cleanup(connection));
                connection.emitter.onTimeout(() -> fail(connection));
                connection.accept();
                if (!connection.isClosed()) {
                    localSseConnections.put(connection.jobId, connection);
                }
            }
            executor.execute(() -> recommend(connection, criteria));
        } catch (IOException | RuntimeException error) {
            try {
                fail(connection);
            } finally {
                releaseJob(connection);
            }
        }
        return connection.emitter;
    }

    public boolean cancelOutingCourseRecommendation(Long memberId) {
        var job = jobs.find(memberId).orElse(null);

        if (job == null
                || !jobs.finish(memberId, job.id(), RecommendationJobStore.State.CANCELLED)) {
            return false;
        }

        var connection = localSseConnections.get(job.id());

        if (connection != null) {
            terminate(connection, "stopped", "");
        }

        return true;
    }

    private void recommend(
            RecommendationConnection connection,
            OutingCriteria criteria
    ) {
        try {
            if (!checkRunning(connection)) {
                return;
            }

            List<OutingGuide> candidates =
                    findCandidates(connection.memberId, criteria);

            if (!checkRunning(connection)) {
                return;
            }

            var response = aiServerService.recommendCourses(
                    criteria,
                    candidates
            );

            if (!checkRunning(connection)) {
                return;
            }

            var data = requireSuccess(response);
            criteriaRepository.deleteById(criteria.getMemberId());
            var result = resultMapper.map(criteria, data);
            complete(connection, "success", Map.of("result", result));
        } catch (RuntimeException error) {
            log.warn(
                    "추천 처리 실패: jobId={}",
                    connection.jobId,
                    error
            );

            fail(connection);
        } finally {
            // 정상 완료, 취소, 오류 모두 실제 실행이 끝나면 정리합니다.
            releaseJob(connection);
        }
    }
    private void complete(
            RecommendationConnection connection,
            String event,
            Object data
    ) {
        synchronized (connection) {
            if (connection.isClosed()) {
                return;
            }

            if (jobs.finish(
                    connection.memberId,
                    connection.jobId,
                    RecommendationJobStore.State.FINISHED
            )) {
                terminate(connection, event, data);
            } else {
                // 취소가 먼저 확정됐다면 stopped로 종료한다.
                checkRunning(connection);
            }
        }
    }

    private void fail(RecommendationConnection connection) {
        try {
            complete(connection, "service-unavailable", unavailable());
        } catch (RuntimeException error) {
            // 저장소 장애 시에도 로컬 SSE 연결은 종료한다.
            log.warn("추천 작업 상태 변경 실패: jobId={}",
                    connection.jobId, error);

            terminate(connection, "service-unavailable", unavailable());
        }
    }

    private boolean checkRunning(RecommendationConnection connection) {
        synchronized (connection) {
            if (connection.isClosed()) {
                return false;
            }

            var job = jobs.find(connection.memberId).orElse(null);

            if (job == null || !job.id().equals(connection.jobId)) {
                terminate(connection, "service-unavailable", unavailable());
                return false;
            }

            if (job.state() == RecommendationJobStore.State.CANCELLED) {
                terminate(connection, "stopped", "");
                return false;
            }

            return job.state() == RecommendationJobStore.State.RUNNING;
        }
    }

    @Scheduled(
            fixedDelayString = "${recommendation.jobs.poll-interval-ms:250}"
    )
    public void checkConnections() {
        for (var connection : localSseConnections.values()) {
            try {
                checkRunning(connection);
            } catch (RuntimeException error) {
                log.warn("추천 작업 상태 조회 실패: jobId={}",
                        connection.jobId, error);

                terminate(connection, "service-unavailable", unavailable());
            }
        }
    }

    private void terminate(
            RecommendationConnection connection,
            String event,
            Object data
    ) {
        synchronized (connection) {
            try {
                connection.finish(event, data);
            } finally {
                cleanup(connection);
            }
        }
    }

    private void cleanup(RecommendationConnection connection) {
        synchronized (connection) {
            connection.close();
            localSseConnections.remove(connection.jobId, connection);
        }
    }

    private void releaseJob(RecommendationConnection connection) {
        try {
            jobs.remove(connection.memberId, connection.jobId);
        } catch (RuntimeException error) {
            log.warn(
                    "추천 작업 정리 실패: jobId={}",
                    connection.jobId,
                    error
            );
        }
    }

    private RecommendCoursesResponseV2.SuccessData requireSuccess(
            RecommendCoursesResponseV2 response
    ) {
        if (response instanceof RecommendCoursesResponseV2.Success(
                String message, RecommendCoursesResponseV2.SuccessData data
        )
                && data != null
                && data.error() == null
                && data.courses() != null
                && (
                "recommend_success".equals(message)
                        || "recommend_not_found".equals(message)
        )) {
            return data;
        }

        throw new ServiceUnavailableException();
    }

    private List<OutingGuide> findCandidates(
            Long memberId,
            OutingCriteria criteria
    ) {
        Coordinate origin;
        try {
            origin = kakaoPlaceSearch.getCoordinates(criteria.getRequestedLocationName());
        } catch (Exception e) {
            throw new IllegalStateException();
        }
        Instant requestedAt = criteria.getRequestedDatetime();

        return collectionRepository.findRecommendationCandidates(memberId)
                .stream()
                .filter(guide -> hasCoordinate(guide.getCoordinate()))
                .filter(guide -> isWithinEventPeriod(guide, requestedAt))
                .filter(guide ->
                        distanceKm(origin, guide.getCoordinate()) <= radiusKm
                )
                .sorted(
                        Comparator.comparingDouble((OutingGuide guide) ->
                                distanceKm(origin, guide.getCoordinate())
                        ).thenComparing(OutingGuide::getId)
                )
                .limit(50)
                .toList();
    }

    private void validate(OutingCriteria criteria) {
        Integer minutes = criteria.getUserAvailableTimeMinutes();

        if (!hasCoordinate(criteria.getUserCoordinate())
                || criteria.getRequestedDatetime() == null
                || criteria.getUserPreferenceSummary() == null
                || minutes == null
                || !List.of(180, 360, 540).contains(minutes)) {
            throw new BadRequestException();
        }
    }

    private boolean hasCoordinate(Coordinate coordinate) {
        return coordinate != null
                && coordinate.lat() != null
                && coordinate.lng() != null
                && Float.isFinite(coordinate.lat())
                && Float.isFinite(coordinate.lng())
                && coordinate.lat() >= -90
                && coordinate.lat() <= 90
                && coordinate.lng() >= -180
                && coordinate.lng() <= 180;
    }

    private double distanceKm(Coordinate from, Coordinate to) {
        double fromLat = Math.toRadians(from.lat());
        double toLat = Math.toRadians(to.lat());

        double deltaLat = toLat - fromLat;
        double deltaLng = Math.toRadians(
                (double) to.lng() - from.lng()
        );

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(fromLat) * Math.cos(toLat)
                * Math.pow(Math.sin(deltaLng / 2), 2);

        return 6371.0088 * 2
                * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }

    private boolean isWithinEventPeriod(
            OutingGuide guide,
            Instant requestedAt
    ) {
        if (!(guide instanceof OutingEvent event)) {
            return true;
        }

        Instant start = event.getStartDatetime();
        Instant end = event.getEndDatetime();

        boolean hasStarted =
                start == null || !requestedAt.isBefore(start);

        boolean hasNotEnded =
                end == null || requestedAt.isBefore(end);

        return hasStarted && hasNotEnded;
    }

    private ProblemDetail unavailable() {
        return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }
}