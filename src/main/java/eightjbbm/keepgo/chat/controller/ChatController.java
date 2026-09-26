package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.ChatMapper;
import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/user/chat-messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;


    /// 채팅 전송 API
    /// @param jwt
    /// @param request {@link SendChatRequest}
    /// @return {@link SendChatResponse}
    @PostMapping
    public ResponseEntity<SendChatResponse> sendChat(
            @AuthenticationPrincipal Jwt jwt,
            SendChatRequest request
    ) {
        var command = ChatMapper.INSTANCE.toSendChatCommand(
                Long.valueOf(jwt.getSubject()),
                request
        );

        SendChatResult result = chatService.sendChat(command);
        String location = "/api/v1/user/chat-messages/" + result.chatId() + "/response";

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header("Location", location)
                .body(
                        SendChatResponse.from(
                                result
                        )
                );
    }

    /// 의도 카드 업데이트 API
    @PatchMapping("/slot")
    public ResponseEntity<?> updateSlot(
            @AuthenticationPrincipal Jwt jwt,
            UpdateSlotRequest request
    ) {
        var command = ChatMapper.INSTANCE.toUpdateSlotCommand(
                Long.valueOf(jwt.getSubject()),
                request
        );

        UpdateSlotResult result = chatService.updateSlot(command);
        return Optional.ofNullable(result.userLocationName())
                .map(value -> ResponseEntity
                        .status(HttpStatus.OK)
                        .body(value))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NO_CONTENT).build());
    }

    /// 챗봇의 대답 조회 API
    /// @param jwt
    /// @param chatId
    /// @return {@link GetReplyResponse}
    @GetMapping("/{chatId}/response")
    public ResponseEntity<GetReplyResponse> getReply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long chatId
    ) {
        var command = ChatMapper.INSTANCE.toGetReplyCommand(
                Long.valueOf(jwt.getSubject()),
                chatId
        );
        GetReplyResult reply = chatService.getReply(command);
        GetReplyResponse response = switch (reply) {
            case GetReplyResult.InProgress inProgress -> GetReplyResponse.InProgress.from(inProgress);
            case GetReplyResult.Completed completed -> GetReplyResponse.Completed.from(completed);
        };
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /// 채팅 내역 조회 API
    /// @param jwt
    /// @param cursor
    /// @param size
    /// @return {@link GetChatsResponse}
    @GetMapping
    public ResponseEntity<GetChatsResponse> getChats(
            @AuthenticationPrincipal Jwt jwt,
            Long cursor,
            Integer size
    ) {
        var command = ChatMapper.INSTANCE.toGetChatsCommand(
                Long.valueOf(jwt.getSubject()),
                cursor,
                size
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        GetChatsResponse.from(
                                chatService.getChats(command)
                        )
                );
    }

    /// 채팅 내역 초기화 API
    /// @param jwt
    @DeleteMapping
    public ResponseEntity<Void> resetChatroom(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var command = ChatMapper.INSTANCE.toResetChatroomCommand(
                Long.valueOf(jwt.getSubject())
        );

        chatService.resetChatroom(command);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
