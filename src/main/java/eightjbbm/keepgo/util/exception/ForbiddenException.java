package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ForbiddenException extends ErrorResponseException {
    public ForbiddenException() {
        super(
                HttpStatus.FORBIDDEN,
                ProblemDetail.forStatus(HttpStatus.FORBIDDEN),
                null
        );
    }
}
