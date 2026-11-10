package eightjbbm.keepgo.util.dto;

public record WarmUpChatbotResponse(
        String message,
        /// 응답 계약이 확정되지 않아 Object로 둔다.
        Object data) {}
