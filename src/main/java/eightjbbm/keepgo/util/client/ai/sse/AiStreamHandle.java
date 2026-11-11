package eightjbbm.keepgo.util.client.ai.sse;

import org.apache.hc.client5.http.classic.methods.HttpPost;

/// 진행 중인 AI 스트림 호출의 취소 핸들.
///
/// `cancel()`은 스트림이 시작되기 전에 불러도, 어느 스레드에서 불러도 된다. 취소된 호출은 AI 연결을 즉시 끊고
/// `AiStreamException`으로 끝난다. `onOpen`은 AI가 `text/event-stream` 응답을 열었을 때 한 번 불린다.
public final class AiStreamHandle {
    private final Runnable onOpen;
    private volatile HttpPost request;
    private volatile boolean cancelled;

    public AiStreamHandle() {
        this(() -> {});
    }

    public AiStreamHandle(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    public void cancel() {
        cancelled = true;
        var running = request;
        if (running != null) running.cancel();
    }

    boolean isCancelled() {
        return cancelled;
    }

    void bind(HttpPost request) {
        this.request = request;
        if (cancelled) request.cancel();
    }

    void opened() {
        onOpen.run();
    }
}
