package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class TooManyRequestsException extends ErrorResponseException {
    public TooManyRequestsException() {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS),
                null
        );
    }
}
