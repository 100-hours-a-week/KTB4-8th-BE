package eightjbbm.keepgo.util.client.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/// AI 전송 한 번의 본문과 종료를 연결한다. URI는 인증정보가 없는 경로만 기록한다.
@Slf4j
public final class AiHttpLog {
    private final String callId = UUID.randomUUID().toString();
    private final long started = System.nanoTime();
    private final String path;
    private final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private volatile Integer status;
    private volatile boolean complete;
    private Charset charset = StandardCharsets.UTF_8;
    private long sequence;
    private String failureReason;

    public AiHttpLog(String method, URI uri, String body) {
        path = escape(uri.getRawPath());
        log.info("AI HTTP request: callId={}, method={}, path={}, body={}", callId, method, path, escape(body));
    }

    public void status(int value) {
        status = value;
        log.info("AI HTTP response: callId={}, path={}, status={}", callId, path, status);
    }

    public String readBody(InputStream input, Charset encoding) throws IOException {
        charset = encoding;
        if (input != null) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1; ) {
                synchronized (received) {
                    received.write(buffer, 0, count);
                }
            }
        }
        complete = true;
        synchronized (received) {
            return received.toString(charset);
        }
    }

    public void failure(String reason) {
        if (failureReason == null) failureReason = reason;
    }

    public byte[] bodyBytes() {
        synchronized (received) {
            return received.toByteArray();
        }
    }

    public void event(String name, String data) {
        event(name, data, true);
    }

    public void event(String name, String data, boolean eventComplete) {
        log.info(
                "AI SSE event: callId={}, path={}, status={}, sequence={}, event={}, body={}, complete={}",
                callId,
                path,
                status,
                ++sequence,
                escape(name),
                escape(data),
                eventComplete);
    }

    public void end(String reason, boolean responseBody) {
        if ("stream_error".equals(reason) && failureReason != null) reason = failureReason;
        if (responseBody) {
            synchronized (received) {
                log.info(
                        "AI HTTP body: callId={}, path={}, status={}, complete={}, body={}",
                        callId,
                        path,
                        status,
                        complete,
                        escape(received.toString(charset)));
            }
        }
        log.info(
                "AI HTTP end: callId={}, path={}, status={}, elapsedMs={}, reason={}",
                callId,
                path,
                status,
                (System.nanoTime() - started) / 1_000_000,
                escape(reason));
    }

    /// JSON 문자열과 같은 이스케이프로 제어문자와 역슬래시를 복원 가능하게 보존한다.
    public static String escape(String text) {
        if (text == null) return "null";
        var safe = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            switch (value) {
                case '\\' -> safe.append("\\\\");
                case '\n' -> safe.append("\\n");
                case '\r' -> safe.append("\\r");
                case '\t' -> safe.append("\\t");
                default -> {
                    if (Character.isISOControl(value) || value == '\u2028' || value == '\u2029') {
                        safe.append(String.format("\\u%04x", (int) value));
                    } else safe.append(value);
                }
            }
        }
        return safe.toString();
    }
}
