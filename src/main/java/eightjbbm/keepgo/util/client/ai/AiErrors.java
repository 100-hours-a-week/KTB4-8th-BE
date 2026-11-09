package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import org.springframework.web.ErrorResponseException;

/// AI 서버 공통 오류를 BE 오류로 변환한다.
///
/// LLM 호출 실패(`llm_*`)는 외부 API 오류(502)로 나누고, 그 밖의 AI 서버 오류는 내부 API 오류(503)로 묶는다.
public final class AiErrors {
    private AiErrors() {}

    /// 공통 오류 명세의 HTTP 상태(400·422·429·500·502·504)를 변환한다.
    public static ErrorResponseException fromHttpStatus(int status) {
        return switch (status) {
            case 429 -> new AiLlmRateLimitedException();
            case 502 -> new AiLlmInvalidResponseException();
            case 504 -> new AiLlmTimeoutException();
            default -> new ServiceUnavailableException();
        };
    }

    /// SSE `error` 이벤트의 `message`를 변환한다.
    public static ErrorResponseException fromMessage(String message) {
        return switch (message == null ? "" : message) {
            case "llm_rate_limited" -> new AiLlmRateLimitedException();
            case "llm_invalid_response" -> new AiLlmInvalidResponseException();
            case "llm_timeout" -> new AiLlmTimeoutException();
            default -> new ServiceUnavailableException();
        };
    }
}
