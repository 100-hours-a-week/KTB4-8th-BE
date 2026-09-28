package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateSlotCacheRequest(
        Long memberId,
        Coordinate coordinate,
        String requestedLocationName,
        LocalDateTime requestedDateTime,
        Integer availableTime,
        List<String> categories
) {
    public static UpdateSlotCacheRequest from(
            Long memberId,
            Coordinate coordinate,
            String requestedLocationName,
            LocalDateTime requestedDateTime,
            Integer availableTime,
            List<String> categories
    ) {
        return new UpdateSlotCacheRequest(
                memberId, coordinate, requestedLocationName, requestedDateTime, availableTime, categories
        );
    }
}
