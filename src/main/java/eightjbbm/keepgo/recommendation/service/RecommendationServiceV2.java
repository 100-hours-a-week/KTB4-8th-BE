package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.recommendation.repository.RecommendationJobStore;
import eightjbbm.keepgo.recommendation.service.dto.RecommendCoursesResponseV2;
import eightjbbm.keepgo.util.client.ai.AiErrors;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamEvent;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamException;
import eightjbbm.keepgo.util.exception.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class RecommendationServiceV2 {

    private final AiServerRecommendApiService aiServerService;
    private final OutingCriteriaRepository criteriaRepository;
    private final RecommendationCandidateSelector candidateSelector;
    private final RecommendationResultMapper resultMapper;
    private final RecommendationCourseService recommendationCourseService;
    private final RecommendationJobStore jobs;
    private final Executor executor;
    private final MessageSource messageSource;

    private final ConcurrentMap<String, RecommendationConnection> localSseConnections = new ConcurrentHashMap<>();

    public RecommendationServiceV2(
            AiServerRecommendApiService aiServerService,
            OutingCriteriaRepository criteriaRepository,
            RecommendationCandidateSelector candidateSelector,
            RecommendationResultMapper resultMapper,
            RecommendationCourseService recommendationCourseService,
            RecommendationJobStore jobs,
            @Qualifier("recommendationTaskExecutor") Executor executor,
            MessageSource messageSource) {
        this.aiServerService = aiServerService;
        this.criteriaRepository = criteriaRepository;
        this.candidateSelector = candidateSelector;
        this.resultMapper = resultMapper;
        this.recommendationCourseService = recommendationCourseService;
        this.jobs = jobs;
        this.executor = executor;
        this.messageSource = messageSource;
    }

    public SseEmitter recommendOutingCourse(Long memberId) {
        return start(memberId, false);
    }

    /// v2와 같은 추천을 시작하되 `status` 이벤트를 전달하고, AI 스트림이 열리기 전의 실패는 SSE가 아닌 HTTP 오류로 던진다.
    /// AI 연결을 기다리는 동안 요청 스레드가 막힌다.
    public SseEmitter recommendOutingCourseV3(Long memberId) {
        return start(memberId, true);
    }

    private SseEmitter start(Long memberId, boolean v3) {
        OutingCriteria criteria = criteriaRepository.findById(memberId).orElseThrow(NotFoundException::new);
        validate(criteria);
        var connection = new RecommendationConnection(memberId, v3);
        if (!jobs.start(memberId, connection.jobId)) {
            throw new ConflictException();
        }
        try {
            synchronized (connection) {
                connection.emitter.onCompletion(() -> cleanup(connection));
                connection.emitter.onError(error -> cleanup(connection));
                connection.emitter.onTimeout(() -> fail(connection));
                if (!v3) {
                    connection.accept();
                }
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
        if (v3) {
            awaitAiOpened(connection);
        }
        return connection.emitter;
    }

    private void awaitAiOpened(RecommendationConnection connection) {
        try {
            connection.opened.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            connection.ai.cancel();
            throw new ServiceUnavailableException();
        } catch (ExecutionException error) {
            throw error.getCause() instanceof ErrorResponseException response
                    ? response
                    : new ServiceUnavailableException();
        }
    }

    public boolean cancelOutingCourseRecommendation(Long memberId) {
        var job = jobs.find(memberId).orElse(null);

        if (job == null || !jobs.finish(memberId, job.id(), RecommendationJobStore.State.CANCELLED)) {
            return false;
        }

        var connection = localSseConnections.get(job.id());

        if (connection != null) {
            terminate(connection, "stopped", "");
        }

        return true;
    }

    private void recommend(RecommendationConnection connection, OutingCriteria criteria) {
        try {
            if (!checkRunning(connection)) {
                return;
            }

            List<OutingGuide> candidates = candidateSelector.select(connection.memberId, criteria);

            if (!checkRunning(connection)) {
                return;
            }

            var response = aiServerService.recommendCourses(
                    criteria, candidates, connection.ai, event -> relay(connection, event));

            if (!checkRunning(connection)) {
                return;
            }

            if ("recommend_cancel_accepted".equals(response.message())) {
                complete(connection, "stopped", "");
                return;
            }

            var data = requireSuccess(response);
            criteriaRepository.deleteById(criteria.getMemberId());
            var courses = recommendationCourseService.saveCourses(criteria, data);
            var result = resultMapper.toResult(criteria, courses);
            complete(connection, "success", Map.of("result", result));
        } catch (AiStreamException error) {
            // 취소로 끊긴 경우는 이미 종료됐고, 그 밖의 실패는 AiSseClient가 로그를 남겼다.
            fail(connection, error.error());
        } catch (ErrorResponseException error) {
            log.error("추천 처리 실패: jobId={}", connection.jobId, error);

            fail(connection, error);
        } catch (RuntimeException error) {
            log.error("추천 처리 실패: jobId={}", connection.jobId, error);

            fail(connection, new ServiceUnavailableException());
        } finally {
            // 정상 완료, 취소, 오류 모두 실제 실행이 끝나면 정리합니다.
            connection.opened.completeExceptionally(new ServiceUnavailableException());
            releaseJob(connection);
        }
    }

    private void relay(RecommendationConnection connection, AiStreamEvent event) {
        if (connection.v3
                && "status".equals(event.name())
                && event.data().path("stage").isString()) {
            connection.status(event.data().get("stage").asString());
        }
    }

    private void complete(RecommendationConnection connection, String event, Object data) {
        synchronized (connection) {
            if (connection.isClosed()) {
                return;
            }

            if (jobs.finish(connection.memberId, connection.jobId, RecommendationJobStore.State.FINISHED)) {
                terminate(connection, event, data);
            } else {
                // 취소가 먼저 확정됐다면 stopped로 종료한다.
                checkRunning(connection);
            }
        }
    }

    private void fail(RecommendationConnection connection) {
        fail(connection, new ServiceUnavailableException());
    }

    /// v2는 어떤 실패든 `service-unavailable`로 보내고, v3는 오류 종류에 따라 `bad-gateway` 또는 `service-unavailable`로 보낸다.
    private void fail(RecommendationConnection connection, ErrorResponseException error) {
        if (connection.v3 && connection.opened.completeExceptionally(error)) {
            // FE 응답이 아직 시작되지 않았으므로 이벤트가 아니라 HTTP 오류로 돌려준다.
            cleanup(connection);
            return;
        }

        var event = connection.v3 && error.getStatusCode().value() == 502 ? "bad-gateway" : "service-unavailable";
        Object body =
                connection.v3 ? error.updateAndGetBody(messageSource, LocaleContextHolder.getLocale()) : unavailable();

        try {
            complete(connection, event, body);
        } catch (RuntimeException exception) {
            // 저장소 장애 시에도 로컬 SSE 연결은 종료한다.
            log.warn("추천 작업 상태 변경 실패: jobId={}", connection.jobId, exception);

            terminate(connection, event, body);
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

    @Scheduled(fixedDelayString = "${recommendation.jobs.poll-interval-ms:250}")
    public void checkConnections() {
        for (var connection : localSseConnections.values()) {
            try {
                checkRunning(connection);
            } catch (RuntimeException error) {
                log.warn("추천 작업 상태 조회 실패: jobId={}", connection.jobId, error);

                terminate(connection, "service-unavailable", unavailable());
            }
        }
    }

    private void terminate(RecommendationConnection connection, String event, Object data) {
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

        // 취소·연결 종료 시 진행 중인 AI 스트림을 즉시 끊는다. 이미 끝난 호출에는 영향이 없다.
        connection.ai.cancel();
    }

    private void releaseJob(RecommendationConnection connection) {
        try {
            jobs.remove(connection.memberId, connection.jobId);
        } catch (RuntimeException error) {
            log.warn("추천 작업 정리 실패: jobId={}", connection.jobId, error);
        }
    }

    private RecommendCoursesResponseV2.SuccessData requireSuccess(RecommendCoursesResponseV2.Success response) {
        var data = response.data();

        if (data != null
                && data.error() == null
                && data.courses() != null
                && ("recommend_success".equals(response.message())
                        || "recommend_not_found".equals(response.message()))) {
            return data;
        }

        throw data != null && "recommend_failed".equals(response.message())
                ? AiErrors.fromMessage(data.error())
                : new ServiceUnavailableException();
    }

    private void validate(OutingCriteria criteria) {
        Integer minutes = criteria.getUserAvailableTimeMinutes();

        if (criteria.getUserCoordinate() == null
                || !criteria.getUserCoordinate().isValid()
                || criteria.getRequestedDatetime() == null
                || minutes == null
                || !List.of(180, 360, 540).contains(minutes)) {
            throw new BadRequestException();
        }
    }

    private ProblemDetail unavailable() {
        return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
