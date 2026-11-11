package eightjbbm.keepgo.util.exception;

import eightjbbm.keepgo.util.client.ai.sse.AiStreamException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final MessageSource messageSource;

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponseException(
            ErrorResponseException exception, HttpServletRequest request) {
        if (exception.getStatusCode().is5xxServerError()) {
            log.error("서버 오류가 발생했습니다.", exception);
        }
        ProblemDetail body = exception.updateAndGetBody(messageSource, request.getLocale());
        if (body.getInstance() == null) {
            body.setInstance(URI.create(request.getRequestURI()));
        }
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            if (errorResponse.getStatusCode().is5xxServerError()) {
                log.error("Server error", exception);
            }
            ProblemDetail body = errorResponse.updateAndGetBody(messageSource, request.getLocale());
            if (body.getInstance() == null) {
                body.setInstance(URI.create(request.getRequestURI()));
            }
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(body);
        }
        log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);
        InternalServerErrorException fallback = new InternalServerErrorException();
        ProblemDetail body = fallback.updateAndGetBody(messageSource, request.getLocale());
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(fallback.getStatusCode()).body(body);
    }

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
