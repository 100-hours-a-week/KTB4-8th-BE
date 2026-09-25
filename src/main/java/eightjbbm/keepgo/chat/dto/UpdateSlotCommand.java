package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDate;
import java.util.List;

public record UpdateSlotCommand(
        Long memberId,
        Coordinate userCoordinate,
        String requestedLocationName,
        LocalDate requestedDate,
        Integer requestedTimeSlot,
        Integer availableTime,
        List<String> categories
) {
    public static UpdateSlotCommand from(
            Long memberId,
            Float userLocationLat,
            Float userLocationLng,
            String requestedLocationName,
            LocalDate requestedDate,
            Integer requestedTimeSlot,
            Integer availableTime,
            List<String> categories
    ) {
        return new UpdateSlotCommand(
                memberId,
                new Coordinate(userLocationLat, userLocationLng),
                requestedLocationName,
                requestedDate,
                requestedTimeSlot,
                availableTime,
                categories
        );
    }
}
