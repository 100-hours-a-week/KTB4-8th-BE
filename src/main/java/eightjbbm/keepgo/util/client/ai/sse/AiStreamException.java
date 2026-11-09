package eightjbbm.keepgo.util.client.ai.sse;

import org.springframework.web.ErrorResponseException;

/// AI 스트림 호출 실패. `error()`가 BE 오류 응답 규칙으로 변환한 오류다.
///
/// 스트림 시작 전(HTTP 오류·연결 실패)이면 호출자가 이 오류를 그대로 HTTP 응답으로 돌려줄 수 있다.
/// 스트림 중(`error` 이벤트·끊김)이면 이미 FE 응답을 시작했을 수 있으므로 호출자가 이벤트로 전달한다.
public final class AiStreamException extends RuntimeException {
    public enum Phase {
        BEFORE_STREAM,
        IN_STREAM
    }

    private final Phase phase;
    private final ErrorResponseException error;

    AiStreamException(Phase phase, ErrorResponseException error, Throwable cause) {
        super(phase + ": " + error.getStatusCode(), cause);
        this.phase = phase;
        this.error = error;
    }

    public Phase phase() {
        return phase;
    }

    public ErrorResponseException error() {
        return error;
    }
}
