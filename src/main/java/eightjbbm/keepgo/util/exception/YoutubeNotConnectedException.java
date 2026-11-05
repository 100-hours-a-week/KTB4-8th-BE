package eightjbbm.keepgo.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/// 스트림을 열기 전에 YouTube 연동이 없음을 알린다.
public class YoutubeNotConnectedException extends ErrorResponseException {

    public YoutubeNotConnectedException() {
        super(HttpStatus.CONFLICT, problemDetail(), null);
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Conflict");
        detail.setDetail("유튜브 계정이 연동되어 있지 않습니다. 먼저 연동해 주세요.");
        detail.setProperty("code", "YOUTUBE_NOT_CONNECTED");
        return detail;
    }
}
