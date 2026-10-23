package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ConflictException extends ErrorResponseException {
    public ConflictException() {
        super(
                HttpStatus.CONFLICT,
                ProblemDetail.forStatus(HttpStatus.CONFLICT),
                null
        );
    }
}
