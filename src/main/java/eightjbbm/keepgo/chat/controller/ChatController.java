package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.ChatMapper;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/chat-messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMapper chatMapper;

    /// 의도 카드 업데이트 API
    @PatchMapping("/slot")
    public ResponseEntity<?> updateSlot(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateSlotRequest request) {
        var command = chatMapper.toUpdateSlotCommand(Long.valueOf(jwt.getSubject()), request);
        IO.println("request coordinate: " + request.location());
        IO.println("command coordinate: " + command.userCoordinate());

        UpdateSlotResult result = chatService.updateSlot(command);
        return Optional.ofNullable(result.userLocationName())
                .map(value -> ResponseEntity.status(HttpStatus.OK).body(value))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    /// 채팅 내역 조회 API
    /// @param jwt
    /// @param cursor
    /// @param size
    /// @return {@link GetChatsResponse}
    @GetMapping
    public ResponseEntity<GetChatsResponse> getChats(@AuthenticationPrincipal Jwt jwt, Long cursor, Integer size) {
        var command = chatMapper.toGetChatsCommand(Long.valueOf(jwt.getSubject()), cursor, size);

        return ResponseEntity.status(HttpStatus.OK).body(GetChatsResponse.from(chatService.getChats(command)));
    }

    /// 채팅 내역 초기화 API
    /// @param jwt
    @DeleteMapping
    public ResponseEntity<Void> resetChatroom(@AuthenticationPrincipal Jwt jwt) {
        var command = chatMapper.toResetChatroomCommand(Long.valueOf(jwt.getSubject()));

        chatService.resetChatroom(command);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
