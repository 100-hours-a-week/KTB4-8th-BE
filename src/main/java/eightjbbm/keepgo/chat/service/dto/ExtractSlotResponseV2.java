package eightjbbm.keepgo.chat.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import eightjbbm.keepgo.util.Coordinate;
import java.time.LocalDateTime;
import java.util.Set;

/// `/v3/extract/stream`의 `done` 이벤트 본문이다.
public record ExtractSlotResponseV2(Slot slot, String query, String botMessage) {
    public record Slot(
            Coordinate origin,
            String region,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime datetime,
            String availableTime,
            Set<String> category) {}
}
