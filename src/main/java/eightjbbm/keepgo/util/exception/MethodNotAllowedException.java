package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class MethodNotAllowedException extends ErrorResponseException {
    public MethodNotAllowedException() {
        super(
                HttpStatus.METHOD_NOT_ALLOWED,
                ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED),
                null
        );
    }
}
