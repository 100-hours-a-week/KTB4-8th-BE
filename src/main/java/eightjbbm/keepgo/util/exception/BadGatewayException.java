package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class BadGatewayException extends ErrorResponseException {
    private final String code;

    public BadGatewayException() {
        super(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY), null);
        this.code = null;
    }

    /// 기능별 예외가 고유 `code`를 응답에 싣고 `problem.<code>.type`·`problem.<code>.title`로 type과 title을 조회하게 한다.
    protected BadGatewayException(String code) {
        super(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY), null);
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
