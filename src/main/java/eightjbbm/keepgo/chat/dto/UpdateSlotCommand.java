package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateSlotCommand(
        Long memberId,
        Coordinate userCoordinate,
        String requestedLocationName,
        LocalDateTime requestedDateTime,
        Integer availableTime,
        List<String> categories
) {}
