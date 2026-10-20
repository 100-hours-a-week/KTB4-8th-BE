package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatServiceV2 {
    private final ChatRepository chatRepository;


}
