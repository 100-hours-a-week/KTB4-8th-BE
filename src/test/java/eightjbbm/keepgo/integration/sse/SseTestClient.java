package eightjbbm.keepgo.integration.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/// SSE 스트림을 순서대로 수집하는 테스트 클라이언트.
///
/// `POST` 기반 SSE를 지원하며, 이벤트가 도착한 순서를 그대로 보존한다.
public final class SseTestClient implements AutoCloseable {

    private final HttpClient client;
    private final InputStream body;
    private final BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private final Thread reader;

    private SseTestClient(HttpClient client, InputStream body) {
        this.client = client;
        this.body = body;
        this.reader = Thread.ofVirtual().name("sse-test-client").start(this::read);
    }

    public static SseTestClient open(URI uri, String accessToken) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            response.body().close();
            throw new SseOpenException(response.statusCode(), errorBody);
        }

        return new SseTestClient(client, response.body());
    }

    public SseEvent next(Duration timeout) throws InterruptedException {
        SseEvent event = events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (event != null) {
            return event;
        }
        if (closed.isDone()) {
            throw new IllegalStateException("SSE 스트림이 이벤트를 남기지 않고 종료되었습니다", failure());
        }
        throw new IllegalStateException("SSE 이벤트를 " + timeout + " 안에 받지 못했습니다");
    }

    public List<SseEvent> collectUntilClosed(Duration timeout) throws InterruptedException {
        List<SseEvent> collected = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            SseEvent event = events.poll(50, TimeUnit.MILLISECONDS);
            if (event != null) {
                collected.add(event);
                continue;
            }
            if (closed.isDone() && events.isEmpty()) {
                return collected;
            }
        }
        return collected;
    }

    public void awaitClosed(Duration timeout) throws InterruptedException {
        try {
            closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("SSE 스트림이 " + timeout + " 안에 종료되지 않았습니다", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("SSE 스트림 읽기가 실패했습니다", exception.getCause());
        }
    }

    private void read() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String event = null;
            List<String> data = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    emit(id, event, data);
                    id = null;
                    event = null;
                    data.clear();
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }

                int separator = line.indexOf(':');
                String field = separator < 0 ? line : line.substring(0, separator);
                String value = separator < 0 ? "" : line.substring(separator + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }

                switch (field) {
                    case "id" -> id = value;
                    case "event" -> event = value;
                    case "data" -> data.add(value);
                    default -> {
                        // retry 등 이 테스트 클라이언트가 쓰지 않는 필드는 무시한다.
                    }
                }
            }
            emit(id, event, data);
            closed.complete(null);
        } catch (IOException exception) {
            closed.completeExceptionally(exception);
        }
    }

    private void emit(String id, String event, List<String> data) {
        if (event != null || !data.isEmpty()) {
            events.add(new SseEvent(id, event, String.join("\n", data)));
        }
    }

    private Throwable failure() {
        try {
            closed.getNow(null);
            return null;
        } catch (java.util.concurrent.CompletionException exception) {
            return exception.getCause();
        }
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException ignored) {
            // 이미 닫힌 스트림이다.
        }
        reader.interrupt();
        client.close();
    }
}
