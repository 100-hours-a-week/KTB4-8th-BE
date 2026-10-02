package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.cache.slot.SlotValue;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;

public sealed interface GetReplyResult
        permits GetReplyResult.Completed, GetReplyResult.InProgress {

    /// @param slot AI 응답을 기존 슬롯에 병합한 결과
    record Completed(
            String status,
            Chat chat,
            GetReplyResponse.Slot slot
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, ExtractSlotResponse.ExtractData response, SlotValue mergedSlot) {
            return new Completed(
                    status, new Chat(response.botMessage(), true), GetReplyResponse.Slot.from(mergedSlot)
            );
        }
    }

    record InProgress(
            String status,
            Integer pollAfter
    ) implements GetReplyResult {
        public static GetReplyResult from(String status, Integer pollAfter){
            return new InProgress(status, pollAfter);
        }
    }

    record Chat(
            String content,
            Boolean isByBot
    ) {}
}
