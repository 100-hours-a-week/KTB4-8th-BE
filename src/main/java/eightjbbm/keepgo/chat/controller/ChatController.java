package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping()
    public void sendChat() {

    }

    @GetMapping
    public void getChats() {

    }

    @DeleteMapping
    public void resetChatroom() {

    }
}
