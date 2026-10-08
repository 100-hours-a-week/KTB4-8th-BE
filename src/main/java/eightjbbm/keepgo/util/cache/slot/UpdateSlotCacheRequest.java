package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.chat.dto.GetReplyResponse;
import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record UpdateSlotCacheRequest(
        Long memberId,
        Coordinate coordinate,
        String requestedLocationName,
        LocalDateTime requestedDateTime,
        Integer availableTime,
        Set<String> categories
) {
    public static UpdateSlotCacheRequest from(
            Long memberId,
            Coordinate coordinate,
            String requestedLocationName,
            LocalDateTime requestedDateTime,
            Integer availableTime,
            Set<String> categories
    ) {
        return new UpdateSlotCacheRequest(
                memberId, coordinate, requestedLocationName, requestedDateTime, availableTime, categories
        );
    }
}
