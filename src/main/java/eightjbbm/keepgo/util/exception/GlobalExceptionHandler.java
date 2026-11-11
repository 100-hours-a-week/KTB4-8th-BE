package eightjbbm.keepgo.util.exception;

import eightjbbm.keepgo.util.client.ai.sse.AiStreamException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleMethodArgumentNotValidException() {
        return new UnprocessableContentException();
    }

    /// 호출자가 풀지 않은 AI 스트림 실패의 안전망이다. 실패는 `AiSseClient`가 이미 ERROR로 남겼으므로 다시 남기지 않는다.
    @ExceptionHandler(AiStreamException.class)
    public ErrorResponse handleAiStreamException(AiStreamException exception) {
        return exception.error();
    }
}
