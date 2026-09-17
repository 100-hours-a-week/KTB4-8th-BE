package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping()
    public SendChatResponse sendChat(@AuthenticationPrincipal Jwt jwt, SendChatRequest request) {
        SendChatCommand command = new SendChatCommand(
                Long.valueOf(jwt.getSubject()),
                request.content()
        );

        return SendChatResponse.from(
                chatService.sendChat(command)
        );
    }

    @GetMapping
    public GetChatsResponse getChats(@AuthenticationPrincipal Jwt jwt, @RequestParam Long cursor, @RequestParam Integer size) {
        GetChatsCommand command = new GetChatsCommand(
                Long.valueOf(jwt.getSubject()),
                cursor,
                size
        );

        return GetChatsResponse.from(
                chatService.getChats(command)
        );
    }

    @DeleteMapping
    public ResetChatroomResponse resetChatroom(@AuthenticationPrincipal Jwt jwt) {
        ResetChatroomCommand command = new ResetChatroomCommand(
                Long.valueOf(jwt.getSubject())
        );

        return ResetChatroomResponse.from(
                chatService.resetChatroom(command)
        );
    }
}
