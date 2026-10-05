package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class UnprocessableContentException extends ErrorResponseException {
    public UnprocessableContentException() {
        super(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT),
                null
        );
    }
}
