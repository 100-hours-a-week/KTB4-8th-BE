package eightjbbm.keepgo.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import eightjbbm.keepgo.chat.dto.SendChatResult;
import eightjbbm.keepgo.chat.service.ChatService;
import eightjbbm.keepgo.chat.service.ChatServiceV2;
import eightjbbm.keepgo.util.exception.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@DisplayName("채팅 전송 content 검증")
class ChatContentValidationTest {

    private static final String MAX_CONTENT = "a".repeat(255);
    private static final String TOO_LONG_CONTENT = "a".repeat(256);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatService chatService = mock(ChatService.class);
    private final ChatServiceV2 chatServiceV2 = mock(ChatServiceV2.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7")
                .build();
        mvc = standaloneSetup(new ChatController(chatService), new ChatControllerV2(chatServiceV2))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory) {
                        return jwt;
                    }
                })
                .build();

        when(chatService.sendChat(any())).thenReturn(new SendChatResult(1L, MAX_CONTENT, false));
        when(chatServiceV2.sendChatMessage(any())).thenReturn(new SseEmitter());
    }

    @Test
    @DisplayName("V1은 255자 content를 받아들임")
    void v1AcceptsMaxLengthContent() throws Exception {
        send("/api/v1/user/chat-messages", MAX_CONTENT).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("V1은 256자 content를 400으로 거부하고 서비스를 호출하지 않음")
    void v1RejectsTooLongContent() throws Exception {
        send("/api/v1/user/chat-messages", TOO_LONG_CONTENT).andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("V2는 255자 content를 받아들임")
    void v2AcceptsMaxLengthContent() throws Exception {
        send("/api/v2/member/chat-messages", MAX_CONTENT).andExpect(status().isOk());
    }

    @Test
    @DisplayName("V2는 256자 content를 400으로 거부하고 서비스를 호출하지 않음")
    void v2RejectsTooLongContent() throws Exception {
        send("/api/v2/member/chat-messages", TOO_LONG_CONTENT).andExpect(status().isBadRequest());

        verifyNoInteractions(chatServiceV2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("V2는 빈 content와 공백 content를 400으로 거부함")
    void v2RejectsBlankContent(String content) throws Exception {
        send("/api/v2/member/chat-messages", content).andExpect(status().isBadRequest());

        verifyNoInteractions(chatServiceV2);
    }

    private ResultActions send(String url, String content) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", content))));
    }
}
