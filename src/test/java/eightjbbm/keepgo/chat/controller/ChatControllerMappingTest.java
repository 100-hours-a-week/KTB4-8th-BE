package eightjbbm.keepgo.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import eightjbbm.keepgo.chat.ChatMapper;
import eightjbbm.keepgo.chat.dto.GetChatsResult;
import eightjbbm.keepgo.chat.dto.UpdateSlotResult;
import eightjbbm.keepgo.chat.service.ChatService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@DisplayName("v1 채팅 핸들러 매핑")
class ChatControllerMappingTest {
    @Test
    @DisplayName("전송과 응답 조회 핸들러는 제거된다")
    void removedHandlersAreNotMapped() throws Exception {
        var mvc = standaloneSetup(new ChatController(mock(ChatService.class), Mappers.getMapper(ChatMapper.class)))
                .build();

        var postResult = mvc.perform(post("/api/v1/user/chat-messages"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
        var replyResult = mvc.perform(get("/api/v1/user/chat-messages/1/response"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(postResult.getHandler()).isNull();
        assertThat(replyResult.getHandler()).isNull();
    }

    @Test
    @DisplayName("조건 수정과 내역 조회 및 초기화는 서비스에 위임된다")
    void remainingHandlersDelegate() throws Exception {
        var service = mock(ChatService.class);
        when(service.getChats(any())).thenReturn(GetChatsResult.from(List.of(), false, -1L));
        when(service.updateSlot(any())).thenReturn(new UpdateSlotResult(null));
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7").build();
        var mvc = standaloneSetup(new ChatController(service, Mappers.getMapper(ChatMapper.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory binderFactory) {
                        return jwt;
                    }
                })
                .build();

        mvc.perform(patch("/api/v1/user/chat-messages/slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/user/chat-messages")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/user/chat-messages")).andExpect(status().isNoContent());
        verify(service).updateSlot(any());
        verify(service).getChats(any());
        verify(service).resetChatroom(any());
    }
}
