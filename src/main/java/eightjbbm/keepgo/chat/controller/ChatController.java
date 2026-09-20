package eightjbbm.keepgo.chat.controller;

import eightjbbm.keepgo.chat.dto.*;
import eightjbbm.keepgo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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


    /// 채팅 전송 API
    /// @param jwt
    /// @param request {@link SendChatRequest}
    /// @return {@link SendChatResponse}
    @PostMapping
    public ResponseEntity<SendChatResponse> sendChat(@AuthenticationPrincipal Jwt jwt, SendChatRequest request) {
        SendChatCommand command = new SendChatCommand(
                Long.valueOf(jwt.getSubject()),
                request.content()
        );

        SendChatResult result = chatService.sendChat(command);
        String location = "/api/v1/user/chat-messages/" + result.chatId() + "/response";

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header("Location", location)
                .body(
                        SendChatResponse.from(result)
                );
    }

    /// 챗봇의 대답 조회 API
    /// @param jwt
    /// @param chatId
    /// @return {@link GetReplyResponse}
    @GetMapping("/{chatId}/response")
    public ResponseEntity<GetReplyResponse> getReply(@AuthenticationPrincipal Jwt jwt, @PathVariable Long chatId) {


        //chatService에서 상태 조회해서, 진행 중이면 진행 중 응답, 완료됐으면 완료 응답 반환하기
        //최대한 분리해야 한다. 뭐라도 완성해야 한다.

        return ResponseEntity
                .status(HttpStatus.OK)
                .body();
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
            Integer size) {
        GetChatsCommand command = new GetChatsCommand(
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
    public ResponseEntity<Void> resetChatroom(@AuthenticationPrincipal Jwt jwt) {
        ResetChatroomCommand command = new ResetChatroomCommand(
                Long.valueOf(jwt.getSubject())
        );

        chatService.resetChatroom(command);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
