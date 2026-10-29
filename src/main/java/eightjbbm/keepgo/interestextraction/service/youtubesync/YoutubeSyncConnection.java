package eightjbbm.keepgo.interestextraction.service.youtubesync;

import eightjbbm.keepgo.interestextraction.repository.youtubesync.YoutubeSyncStatus;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// SSE 연결 하나. 실행 인스턴스와 무관하게 동기화 행의 스냅샷만 보고 이벤트를 만든다.
@Slf4j
final class YoutubeSyncConnection {

    static final String STREAM_PATH = "/api/v3/member/youtube-sync";

    private static final String PREFIX = "youtube-sync.";

    private final Long syncId;
    private final SseEmitter emitter;
    private final long keepAliveNanos;

    private boolean closed;
    private long lastEventSeq;
    private long lastSentNanos = System.nanoTime();

    YoutubeSyncConnection(Long syncId, long timeoutMillis, long keepAliveMillis) {
        this.syncId = syncId;
        this.emitter = new SseEmitter(timeoutMillis);
        this.keepAliveNanos = keepAliveMillis * 1_000_000L;
    }

    SseEmitter emitter() {
        return emitter;
    }

    synchronized Long syncId() {
        return syncId;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    /// 연결 직후·재연결 시 항상 첫 이벤트다.
    synchronized void accept(YoutubeSyncSnapshot snapshot) {
        send("accepted", YoutubeSyncEvent.from(snapshot), snapshot.eventSeq());
    }

    /// 아직 보내지 않은 변화가 있으면 progress로 내보낸다.
    synchronized boolean progressIfChanged(YoutubeSyncSnapshot snapshot) {
        if (closed || snapshot.eventSeq() <= lastEventSeq) {
            return false;
        }
        send("progress", YoutubeSyncEvent.from(snapshot), snapshot.eventSeq());
        return true;
    }

    /// 동기화 행이 종료 상태일 때 성공 또는 오류 이벤트를 보내고 연결을 닫는다.
    synchronized void finish(YoutubeSyncSnapshot snapshot) {
        finish(snapshot, snapshot.eventSeq());
    }

    /// 이미 종료된 동기화에 새로 붙었을 때 accepted 다음에 보낼 종료 이벤트.
    synchronized void finishAfterAccept(YoutubeSyncSnapshot snapshot) {
        finish(snapshot, lastEventSeq + 1);
    }

    /// 동기화 행 없이 요청 자체가 거절될 때의 오류 이벤트.
    synchronized void reject(YoutubeSyncError error) {
        send(error.event(), YoutubeSyncErrorPayload.of(UUID.randomUUID().toString(), error), 1);
        complete();
    }

    synchronized void keepAliveIfDue() {
        if (closed || System.nanoTime() - lastSentNanos < keepAliveNanos) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
            lastSentNanos = System.nanoTime();
        } catch (IOException | RuntimeException error) {
            closeWithError(error);
        }
    }

    synchronized void close() {
        closed = true;
    }

    private void finish(YoutubeSyncSnapshot snapshot, long eventId) {
        if (closed) {
            return;
        }

        if (snapshot.status() == YoutubeSyncStatus.SUCCEEDED) {
            send("success", YoutubeSyncEvent.from(snapshot), eventId);
        } else {
            YoutubeSyncError error = errorOf(snapshot.errorCode());
            send(error.event(), YoutubeSyncErrorPayload.of(snapshot.traceId(), error), eventId);
        }
        complete();
    }

    private void send(String event, Object data, long eventId) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(eventId))
                    .name(PREFIX + event)
                    .data(data, MediaType.APPLICATION_JSON));
            lastEventSeq = eventId;
            lastSentNanos = System.nanoTime();
        } catch (IOException | RuntimeException error) {
            closeWithError(error);
        }
    }

    private void complete() {
        closed = true;
        emitter.complete();
    }

    private void closeWithError(Exception error) {
        closed = true;
        if (!(error instanceof IllegalStateException)) {
            log.debug("YouTube 동기화 SSE 전송 실패: syncId={}", syncId, error);
        }
        emitter.completeWithError(error);
    }

    private static YoutubeSyncError errorOf(String code) {
        if (code == null) {
            return YoutubeSyncError.INTERNAL_ERROR;
        }
        try {
            return YoutubeSyncError.valueOf(code);
        } catch (IllegalArgumentException error) {
            return YoutubeSyncError.INTERNAL_ERROR;
        }
    }
}
