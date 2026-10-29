package eightjbbm.keepgo.recommendation.service;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class RecommendationConnection {

    private static final String PREFIX = "outing-course-recommendation.";

    final Long memberId;
    final String jobId = UUID.randomUUID().toString();
    final SseEmitter emitter = new SseEmitter(180_000L);

    private boolean closed;

    RecommendationConnection(Long memberId) {
        this.memberId = memberId;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized void accept() throws IOException {
        if (!closed) {
            send("accepted", "");
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
