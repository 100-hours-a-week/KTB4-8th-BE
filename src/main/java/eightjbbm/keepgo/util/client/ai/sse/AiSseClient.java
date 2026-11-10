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
/// 형식은 AI 서버(FastAPI `ServerSentEvent`)가 내보내는 표준 SSE다. 이벤트마다 `event: <이름>` 줄과 JSON `data:` 줄이 오고
/// `data`는 이벤트 본문 자체다(`delta`는 `{"text"}`, `error`는 `{"message"}`). API 시트의 `{"event", "data"}` 표기는
/// 이 이름과 본문을 한 객체로 보여 주는 설명이며 전송 형식이 아니다. 이름이 없거나 모르는 이벤트와 `:` 주석(keep-alive)은 무시한다.
///
/// 스트림을 시작하기 전 실패(연결 실패, 200이 아닌 상태, `text/event-stream`이 아닌 응답)는 `BEFORE_STREAM`이다.
/// `done`의 본문은 해석 없이 돌려주므로 추천의 `done.data.error`(`recommend_failed`)는 호출자가 처리한다.
///
/// 실패는 이 클래스가 ERROR로 한 번 남긴다. 호출자는 같은 실패를 다시 ERROR로 남기지 않는다.
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
                var errorBody = bodyOf(response);
                var error = AiErrors.fromHttpResponse(response.getCode(), messageOf(errorBody));
                var logged = errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody;
                throw fail(path, Phase.BEFORE_STREAM, error, "http=" + response.getCode() + ", body=" + logged, null);
            }
            var contentType = response.getFirstHeader("Content-Type");
            if (contentType == null
                    || !contentType.getValue().split(";")[0].strip().equalsIgnoreCase("text/event-stream")) {
                throw fail(
                        path,
                        Phase.BEFORE_STREAM,
                        new ServiceUnavailableException(),
                        "content_type=" + (contentType == null ? null : contentType.getValue()),
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
        if (name == null || !EVENTS.contains(name)) return null;
        JsonNode payload;
        try {
            payload = mapper.readTree(data);
        } catch (JacksonException exception) {
            throw fail(path, Phase.IN_STREAM, new ServiceUnavailableException(), "invalid_json", exception);
        }
        switch (name) {
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
            return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), 4096);
        } catch (IOException | ParseException exception) {
            return "";
        }
    }

    /// HTTP 오류 본문 `{"message": ...}`의 사유. JSON이 아니거나 문자열 사유가 없으면 null.
    private String messageOf(String body) {
        try {
            var message = mapper.readTree(body).path("message");
            return message.isString() ? message.asString() : null;
        } catch (JacksonException exception) {
            return null;
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
