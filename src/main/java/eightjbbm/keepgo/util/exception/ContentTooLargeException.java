package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ContentTooLargeException extends ErrorResponseException {
    public ContentTooLargeException() {
        super(
                HttpStatus.CONTENT_TOO_LARGE,
                ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE),
                null
        );
    }
}
