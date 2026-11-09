package eightjbbm.keepgo.util.client.ai.sse;

import eightjbbm.keepgo.util.client.ai.AiErrors;
import eightjbbm.keepgo.util.client.ai.sse.AiStreamException.Phase;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.web.ErrorResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// 추출과 추천이 공유하는 AI SSE 수신 규칙.
///
/// `delta`는 순서대로 이어 붙이고, `done` 또는 `error`를 받으면 종료한다. 둘 다 없이 연결이 끊기거나
/// 이벤트 사이 지연이 `idleTimeout`을 넘으면 실패이며, 자동 재시도 없이 호출자가 새로 요청한다.
///
/// 이벤트는 `event:` 줄과 JSON `data:`로 읽고, `data`가 `{"event": ..., "data": ...}` 봉투이면 봉투를 풀어 읽는다.
@Slf4j
public final class AiSseClient implements AutoCloseable {
    private static final Set<String> EVENTS = Set.of("delta", "status", "done", "error");

    private final URI baseUri;
    private final ObjectMapper mapper;
    private final CloseableHttpClient httpClient;

    // shortcut: 이벤트 사이 지연만 제한하고 스트림 전체 시간은 제한하지 않는다, 전체 상한이 필요해지면 호출자에서 마감 시각을 넘긴다.
    public AiSseClient(URI baseUri, ObjectMapper mapper, Duration connectTimeout, Duration idleTimeout) {
        this.baseUri = baseUri;
        this.mapper = mapper;
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(idleTimeout.toMillis()))
                        .build())
                .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();
    }

    /// `path`로 `body`를 POST하고 스트림이 `done`으로 끝날 때까지 읽는다. `delta`·`status`는 도착하는 대로 `onEvent`에 전달한다.
    ///
    /// @throws AiStreamException `done` 없이 끝났거나 `error`·HTTP 오류를 받은 경우
    public AiStreamResult stream(String path, Object body, Consumer<AiStreamEvent> onEvent) {
        var request = new HttpPost(baseUri.resolve(path.startsWith("/") ? path : "/" + path));
        request.setHeader("Accept", "text/event-stream");
        request.setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        ClassicHttpResponse response;
        try {
            response = httpClient.executeOpen(null, request, null);
        } catch (IOException exception) {
            throw fail(path, Phase.BEFORE_STREAM, new ServiceUnavailableException(), "connect", exception);
        }
        try {
            if (response.getCode() != 200) {
                throw fail(
                        path,
                        Phase.BEFORE_STREAM,
                        AiErrors.fromHttpStatus(response.getCode()),
                        "http=" + response.getCode() + ", body=" + bodyOf(response),
                        null);
            }
            return read(path, response, onEvent);
        } finally {
            // 남은 본문을 기다리지 않도록 연결부터 중단한다.
            request.cancel();
            try {
                response.close();
            } catch (IOException exception) {
                log.debug("AI 스트림 응답 닫기 실패: path={}", path, exception);
            }
        }
    }

    private AiStreamResult read(String path, ClassicHttpResponse response, Consumer<AiStreamEvent> onEvent) {
        var text = new StringBuilder();
        var data = new StringBuilder();
        String name = null;
        try {
            var reader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        AiStreamResult result = dispatch(path, name, data.toString(), text, onEvent);
                        if (result != null) return result;
                    }
                    name = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                }
            }
        } catch (IOException exception) {
            throw fail(path, Phase.IN_STREAM, new ServiceUnavailableException(), "io", exception);
        }
        // 마지막 이벤트가 빈 줄로 끝나지 않은 채 끊긴 경우도 여기로 온다.
        throw fail(path, Phase.IN_STREAM, new ServiceUnavailableException(), "eof", null);
    }

    private AiStreamResult dispatch(
            String path, String name, String data, StringBuilder text, Consumer<AiStreamEvent> onEvent) {
        if (name != null && !EVENTS.contains(name)) return null;
        JsonNode payload;
        try {
            payload = mapper.readTree(data);
            if (name == null && payload.hasNonNull("event")) {
                name = payload.get("event").asString();
                payload = payload.path("data");
            }
        } catch (JacksonException exception) {
            throw fail(path, Phase.IN_STREAM, new ServiceUnavailableException(), "invalid_json", exception);
        }
        switch (name == null ? "" : name) {
            case "delta" -> {
                if (!payload.path("text").isString()) {
                    throw fail(path, Phase.IN_STREAM, new ServiceUnavailableException(), "delta_without_text", null);
                }
                text.append(payload.get("text").asString());
                onEvent.accept(new AiStreamEvent("delta", payload));
            }
            case "status" -> onEvent.accept(new AiStreamEvent("status", payload));
            case "done" -> {
                return new AiStreamResult(text.toString(), payload);
            }
            case "error" -> {
                var message = payload.path("message");
                String reason = message.isString() ? message.asString() : null;
                throw fail(path, Phase.IN_STREAM, AiErrors.fromMessage(reason), "error=" + reason, null);
            }
            default -> {}
        }
        return null;
    }

    private static String bodyOf(ClassicHttpResponse response) {
        try {
            var body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            return body.length() > 200 ? body.substring(0, 200) : body;
        } catch (IOException | ParseException exception) {
            return "";
        }
    }

    private static AiStreamException fail(
            String path, Phase phase, ErrorResponseException error, String reason, Throwable cause) {
        log.error(
                "AI 스트림 실패: path={}, phase={}, status={}, reason={}",
                path,
                phase,
                error.getStatusCode().value(),
                reason,
                cause);
        return new AiStreamException(phase, error, cause);
    }

    @Override
    public void close() {
        httpClient.close(CloseMode.IMMEDIATE);
    }
}
