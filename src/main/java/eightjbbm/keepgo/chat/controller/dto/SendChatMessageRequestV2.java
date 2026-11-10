package eightjbbm.keepgo.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequestV2(
        @NotBlank @Size(max = 255) String content) {}
