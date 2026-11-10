package eightjbbm.keepgo.util.client.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.ErrorResponseException;

@DisplayName("AI LLM 오류 응답")
class AiLlmExceptionsTest {

    @ParameterizedTest(name = "{0}")
    @DisplayName("고유 code와 messages.properties의 type·title이 502 응답에 실린다")
    @CsvSource({
        "llm_timeout,AI_LLM_TIMEOUT,/problems/ai-llm-timeout,AI 응답 시간이 초과되었습니다.",
        "llm_rate_limited,AI_LLM_RATE_LIMITED,/problems/ai-llm-rate-limited,AI 요청이 일시적으로 제한되었습니다.",
        "llm_invalid_response,AI_LLM_INVALID_RESPONSE,/problems/ai-llm-invalid-response,AI가 올바른 응답을 만들지 못했습니다."
    })
    void carriesCodeTypeAndTitle(String message, String code, String type, String title) {
        ErrorResponseException exception = AiErrors.fromMessage(message);
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var body = exception.updateAndGetBody(messageSource, Locale.KOREA);

        assertThat(exception.getStatusCode().value()).isEqualTo(502);
        assertThat(body.getProperties()).containsEntry("code", code);
        assertThat(body.getType()).hasToString(type);
        assertThat(body.getTitle()).isEqualTo(title);
    }
}
