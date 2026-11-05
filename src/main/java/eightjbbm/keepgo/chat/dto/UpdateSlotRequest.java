package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;
import java.time.LocalDateTime;
import java.util.Set;

public record UpdateSlotRequest(
        Coordinate location,
        String requestedLocationName,
        LocalDateTime requestedDateTime,
        Integer availableTime,
        Set<String> categories) {}
