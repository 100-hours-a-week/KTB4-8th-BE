package eightjbbm.keepgo.util.dto;

public record ExtractSlotResponse(
        String message,
        ExtractData data
) {
    /// @param slot AI가 이전 슬롯과 이번 발화를 합쳐 돌려준 슬롯 (AI 형식)
    /// @param query 정성 조건
    /// @param botMessage 사용자에게 보여줄 챗봇 답변
    public record ExtractData(
            AiSlot slot,
            String query,
            String botMessage
    ) {}
}
