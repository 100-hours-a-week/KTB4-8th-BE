package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDate;
import java.util.List;

public record UpdateSlotRequest(
        Coordinate location,
        String requestedLocationName,
        LocalDate requestedDate,
        Integer requestedTimeSlot,
        Integer availableTime,
        List<String> categories
) {
}
