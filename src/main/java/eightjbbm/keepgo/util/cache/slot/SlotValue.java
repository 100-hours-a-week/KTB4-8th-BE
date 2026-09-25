package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SlotValue {
    private Coordinate coordinate;
    private String requestedLocationName;
    private LocalDate requestedDate;
    private Integer requestedTimeSlot;
    private Integer availableTime;
    private List<String> categories;

    public static SlotValue from(
            Float lat,
            Float lng,
            String requestedLocationName,
            LocalDate requestedDate,
            Integer requestedTimeSlot,
            Integer availableTime,
            List<String> categories
    ) {
        return new SlotValue(
                new Coordinate(lat, lng),
                requestedLocationName,
                requestedDate,
                requestedTimeSlot,
                availableTime,
                categories
        );
    }
}
