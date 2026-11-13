package eightjbbm.keepgo.util.client.ai.sse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eightjbbm.keepgo.util.client.ai.AiErrors;
import eightjbbm.keepgo.util.exception.GlobalExceptionHandler;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("AI 스트림 실패의 전역 처리")
class AiStreamExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/before")
        void before() {
            throw new AiStreamException(
                    AiStreamException.Phase.BEFORE_STREAM, AiErrors.fromMessage("llm_timeout"), null);
        }

        @GetMapping("/in-stream")
        void inStream() {
            throw new AiStreamException(
                    AiStreamException.Phase.IN_STREAM, new ServiceUnavailableException(), new RuntimeException());
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(
                    new GlobalExceptionHandler(new org.springframework.context.support.StaticMessageSource()))
            .build();

    @Test
    @DisplayName("스트림 시작 전 실패는 error()의 502 응답과 고유 code로 변환한다")
    void convertsBeforeStreamFailureToBadGateway() throws Exception {
        mockMvc.perform(get("/before"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_LLM_TIMEOUT"));
    }

    @Test
    @DisplayName("스트림 중 실패도 phase와 무관하게 error()의 503 응답으로 변환한다")
    void convertsInStreamFailureToServiceUnavailable() throws Exception {
        mockMvc.perform(get("/in-stream")).andExpect(status().isServiceUnavailable());
    }
}
