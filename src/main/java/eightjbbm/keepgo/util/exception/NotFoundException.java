package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class NotFoundException extends ErrorResponseException {
    private final String code;

    public NotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatus(HttpStatus.NOT_FOUND), null);
        this.code = null;
    }

    protected NotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatus(HttpStatus.NOT_FOUND), null);
        this.code = code;
        getBody().setProperty("code", code);
    }

    @Override
    public String getTypeMessageCode() {
        return code == null ? super.getTypeMessageCode() : "problem." + code + ".type";
    }

    @Override
    public String getTitleMessageCode() {
        return code == null ? super.getTitleMessageCode() : "problem." + code + ".title";
    }
}
