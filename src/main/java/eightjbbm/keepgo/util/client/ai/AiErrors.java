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

    /// HTTP 오류 응답을 변환한다. 본문 `message`가 `llm_*` 사유이면 그것을 우선하고, 아니면 상태로 분류한다.
    /// 본문이 없거나 JSON이 아니면(프록시 응답 등) 상태 매핑을 쓴다.
    public static ErrorResponseException fromHttpResponse(int status, String message) {
        var byMessage = fromMessage(message);
        return byMessage instanceof ServiceUnavailableException ? fromHttpStatus(status) : byMessage;
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
