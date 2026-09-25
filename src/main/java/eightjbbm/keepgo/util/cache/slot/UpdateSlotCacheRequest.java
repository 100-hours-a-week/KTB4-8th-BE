package eightjbbm.keepgo.util.cache.slot;

import java.time.LocalDate;
import java.util.List;

public record UpdateSlotCacheRequest(
        Long memberId,
        Float lat,
        Float lng,
        String requestedLocationName,
        LocalDate requestedDate,
        Integer requestedTimeSlot,
        Integer availableTime,
        List<String> categories
) {
    public static UpdateSlotCacheRequest from(
            Long memberId,
            Float lat,
            Float lng,
            String requestedLocationName,
            LocalDate requestedDate,
            Integer requestedTimeSlot,
            Integer availableTime,
            List<String> categories
    ) {
        return new UpdateSlotCacheRequest(
                memberId, lat, lng, requestedLocationName, requestedDate, requestedTimeSlot, availableTime, categories
        );
    }
}
