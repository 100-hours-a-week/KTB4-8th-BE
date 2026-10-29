package eightjbbm.keepgo.integration.sse;

public class SseOpenException extends RuntimeException {

    private final int status;
    private final String body;

    public SseOpenException(int status, String body) {
        super("SSE 스트림을 열지 못했습니다: status=" + status + ", body=" + body);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
