package eightjbbm.keepgo.recommendation.service;

import eightjbbm.keepgo.util.client.ai.sse.AiStreamHandle;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class RecommendationConnection {

    private static final String PREFIX = "outing-course-recommendation.";

    final Long memberId;
    final boolean v3;
    final String jobId = UUID.randomUUID().toString();
    final SseEmitter emitter = new SseEmitter(180_000L);
    /// v3에서 AI 스트림이 열리면 완료되고, 열리기 전에 실패하면 예외로 완료된다. 이때까지 FE 응답은 시작하지 않는다.
    final CompletableFuture<Void> opened = new CompletableFuture<>();

    final AiStreamHandle aiStream = new AiStreamHandle(this::onAiOpened);

    private boolean closed;

    RecommendationConnection(Long memberId, boolean v3) {
        this.memberId = memberId;
        this.v3 = v3;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized void accept() throws IOException {
        if (!closed) {
            send("accepted", "");
        }
    }

    /// 진행 단계를 전달한다. 연결이 끊긴 경우는 emitter 콜백이 정리하므로 여기서는 무시한다.
    synchronized void status(String stage) {
        if (closed) {
            return;
        }

        try {
            send("status", Map.of("stage", stage));
        } catch (IOException | RuntimeException error) {
            // 전달하지 못한 진행 단계는 건너뛴다.
        }
    }

    synchronized void finish(String event, Object data) {
        if (closed) {
            return;
        }

        closed = true;

        try {
            send(event, data);
            emitter.complete();
        } catch (IOException | RuntimeException error) {
            emitter.completeWithError(error);
        }
    }

    synchronized void close() {
        closed = true;
    }

    private void onAiOpened() {
        if (v3) {
            try {
                accept();
            } catch (IOException | RuntimeException error) {
                // 연결이 끊긴 경우는 emitter 콜백이 정리한다.
            }
        }

        opened.complete(null);
    }

    private void send(String event, Object data) throws IOException {
        var builder = SseEmitter.event().id(jobId).name(PREFIX + event);

        if (data instanceof String text) {
            builder.data(text);
        } else {
            builder.data(data, MediaType.APPLICATION_JSON);
        }

        emitter.send(builder);
    }
}
