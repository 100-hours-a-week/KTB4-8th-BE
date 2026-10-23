package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record UpdateSlotCommand(
        Long memberId,
        Coordinate userCoordinate,
        String requestedLocationName,
        LocalDateTime requestedDateTime,
        Integer availableTime,
        Set<String> categories
) {}
