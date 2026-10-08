package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ServiceUnavailableException extends ErrorResponseException {
    public ServiceUnavailableException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                null
        );
    }
}
