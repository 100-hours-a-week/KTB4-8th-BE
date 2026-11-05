package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.controller.dto.SendChatMessageRequestV2;
import eightjbbm.keepgo.chat.service.ChatServiceV2;
import eightjbbm.keepgo.chat.service.dto.SendChatMessageCommandV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class ChatControllerV2 {

    private final ChatServiceV2 chatService;

    @PostMapping("/api/v2/member/chat-messages")
    public ResponseEntity<SseEmitter> sendChatMessage(
            @Valid @RequestBody SendChatMessageRequestV2 request, @AuthenticationPrincipal Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.sendChatMessage(new SendChatMessageCommandV2(memberId, request.content())));
    }
}
