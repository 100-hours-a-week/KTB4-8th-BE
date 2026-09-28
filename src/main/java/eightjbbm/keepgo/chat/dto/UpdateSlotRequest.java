package eightjbbm.keepgo.chat.dto;

import eightjbbm.keepgo.util.Coordinate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateSlotRequest(
        @NotNull Coordinate location,
        @NotBlank String requestedLocationName,
        @NotNull LocalDateTime requestedDateTime,
        @NotNull Integer availableTime,
        @NotNull List<String> categories
) {
}
