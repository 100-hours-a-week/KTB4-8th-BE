package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class BadGatewayException extends ErrorResponseException {
    public BadGatewayException() {
        super(
                HttpStatus.BAD_GATEWAY,
                ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY),
                null
        );
    }
}
