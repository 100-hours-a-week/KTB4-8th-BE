package eightjbbm.keepgo.util.client.ai.job;

public final class AiRequestException extends RuntimeException {
    public enum Kind {
        INVALID_AI_RESPONSE,
        HTTP_ERROR,
        TRANSPORT_ERROR,
        TIMEOUT,
        INTERRUPTED
    }

    private final Kind kind;
    private final Integer statusCode;
    private final String retryAfter;

    public AiRequestException(Kind kind, Integer statusCode, String retryAfter, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String retryAfter() {
        return retryAfter;
    }
}
