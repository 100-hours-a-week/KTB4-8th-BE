package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class UnsupportedMediaTypeException extends ErrorResponseException {
    public UnsupportedMediaTypeException() {
        super(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                null
        );
    }
}
