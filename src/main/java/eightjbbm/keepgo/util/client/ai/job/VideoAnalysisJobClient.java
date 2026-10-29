package eightjbbm.keepgo.util.client.ai.job;

import static eightjbbm.keepgo.util.client.ai.job.AiRequestException.Kind.*;

import eightjbbm.keepgo.common.domain.BusinessScheduleDto;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** Job 실행 계층 전용. 호출 예산과 재시도는 상위 Job 계층이 관리한다. */
public final class VideoAnalysisJobClient implements AutoCloseable {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(130);
    private final URI endpoint;
    private final ObjectMapper mapper;
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public VideoAnalysisJobClient(URI endpoint, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.mapper = mapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(
                        LogicalType.Textual,
                        config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT.toMillis()))
                        .build())
                .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connections)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();
    }

    public VideoAnalysisResponse analyzeVideo(List<String> videoIds, Duration remainingJobTime) {
        long started = System.nanoTime();
        if (remainingJobTime == null || remainingJobTime.isNegative() || remainingJobTime.isZero()) {
            throw new AiRequestException(TIMEOUT, null, null, null);
        }
        if (videoIds == null
                || videoIds.isEmpty()
                || videoIds.size() > 10
                || videoIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(videoIds).size() != videoIds.size()) {
            throw new IllegalArgumentException("분석 배치는 중복 없는 영상 ID 1~10개여야 합니다");
        }
        Duration budget = remainingJobTime.compareTo(ANALYSIS_TIMEOUT) < 0 ? remainingJobTime : ANALYSIS_TIMEOUT;
        var urls = new LinkedHashMap<String, String>();
        videoIds.forEach(id -> urls.put("https://youtube.com/shorts/" + id, id));
        var body = mapper.writeValueAsString(Map.of("video_urls", urls.keySet()));
        long remainingNanos = budget.toNanos() - (System.nanoTime() - started);
        if (remainingNanos <= 0) throw new AiRequestException(TIMEOUT, null, null, null);
        var request = new HttpPost(endpoint);
        request.setHeader("Accept", "application/json");
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        var pending = executor.submit(() -> {
            try (var response = httpClient.executeOpen(null, request, null)) {
                if (response.getCode() != 200) {
                    var retryAfter = response.getFirstHeader("Retry-After");
                    var failure = new AiRequestException(
                            HTTP_ERROR, response.getCode(), retryAfter == null ? null : retryAfter.getValue(), null);
                    // 응답을 닫으며 오류 본문을 기다리지 않도록 연결부터 중단한다.
                    request.cancel();
                    throw failure;
                }
                return response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        });
        try {
            // body 전체 수신까지 제한한다. 응답 헤더 수신만으로 타임아웃을 해제하지 않는다.
            remainingNanos = budget.toNanos() - (System.nanoTime() - started);
            var response = pending.get(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS);
            return decode(response, urls);
        } catch (TimeoutException exception) {
            request.cancel();
            pending.cancel(true);
            throw new AiRequestException(TIMEOUT, null, null, exception);
        } catch (InterruptedException exception) {
            request.cancel();
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiRequestException(INTERRUPTED, null, null, exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof AiRequestException failure) throw failure;
            throw new AiRequestException(
                    cause instanceof SocketTimeoutException ? TIMEOUT : TRANSPORT_ERROR, null, null, cause);
        }
    }

    private VideoAnalysisResponse decode(String body, Map<String, String> requestedUrls) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode rows = root.path("data").path("results");
            require("analyze_success".equals(root.path("message").asString()) && rows.isArray());
            var results = new LinkedHashMap<String, VideoAnalysisResponse.Result>();
            for (JsonNode row : rows) {
                require(row.isObject()
                        && row.path("video_url").isString()
                        && row.path("status").isString()
                        && row.has("error"));
                String id = requestedUrls.get(row.get("video_url").asString());
                require(id != null);
                var status = switch (row.get("status").asString()) {
                    case "success" -> VideoAnalysisResponse.Status.SUCCESS;
                    case "not_found" -> VideoAnalysisResponse.Status.NOT_FOUND;
                    case "failed" -> VideoAnalysisResponse.Status.FAILED;
                    default -> throw new IllegalArgumentException("알 수 없는 분석 상태");
                };
                var previous = results.get(id);
                require(previous == null
                        || (previous.status() == status && status == VideoAnalysisResponse.Status.SUCCESS));
                var error = row.get("error");
                require(
                        status == VideoAnalysisResponse.Status.FAILED
                                ? error.isString() && !error.asString().isBlank()
                                : error.isNull());
                var guides = previous == null
                        ? new ArrayList<VideoAnalysisResponse.Guide>()
                        : new ArrayList<>(previous.guides());
                if (status == VideoAnalysisResponse.Status.SUCCESS) {
                    require(row.path("place_type").isString());
                    var type = switch (row.get("place_type").asString()) {
                        case "장소" -> VideoAnalysisResponse.GuideType.PLACE;
                        case "이벤트" -> VideoAnalysisResponse.GuideType.EVENT;
                        default -> throw new IllegalArgumentException("알 수 없는 가이드 유형");
                    };
                    for (String field : List.of("event_start_date", "event_end_date")) {
                        if (row.hasNonNull(field)) {
                            require(row.get(field).isString());
                            LocalDate.parse(row.get(field).asString());
                        }
                    }
                    var candidate = mapper.treeToValue(row, WireCandidate.class);
                    guides.add(new VideoAnalysisResponse.Guide(
                            type,
                            candidate.placeName(),
                            candidate.address(),
                            candidate.lat(),
                            candidate.lng(),
                            candidate.summary(),
                            candidate.locationDetail(),
                            candidate.category(),
                            candidate.businessHours(),
                            candidate.eventStartDate(),
                            candidate.eventEndDate(),
                            candidate.eventPeriodUnresolved(),
                            candidate.confidence()));
                } else {
                    // 실패/미발견 행에 후보 데이터가 섞이면 성공 결과로 조용히 처리하지 않는다.
                    for (String field : List.of(
                            "place_type",
                            "place_name",
                            "address",
                            "lat",
                            "lng",
                            "summary",
                            "category",
                            "location_detail",
                            "business_hours",
                            "event_start_date",
                            "event_end_date",
                            "event_period_unresolved",
                            "confidence")) {
                        require(!row.hasNonNull(field));
                    }
                }
                results.put(
                        id,
                        new VideoAnalysisResponse.Result(
                                id,
                                status,
                                error.isNull() ? null : new VideoAnalysisResponse.Failure(error.asString(), null),
                                List.copyOf(guides)));
            }
            require(results.keySet().equals(new HashSet<>(requestedUrls.values())));
            return new VideoAnalysisResponse(List.copyOf(results.values()));
        } catch (RuntimeException exception) {
            throw new AiRequestException(INVALID_AI_RESPONSE, 200, null, exception);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("분석 응답 구조 위반");
    }

    private record WireCandidate(
            String placeName,
            String address,
            Double lat,
            Double lng,
            String summary,
            String locationDetail,
            String category,
            BusinessScheduleDto businessHours,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            Boolean eventPeriodUnresolved,
            Double confidence) {}

    @Override
    public void close() {
        executor.shutdownNow();
        httpClient.close(CloseMode.IMMEDIATE);
    }
}
