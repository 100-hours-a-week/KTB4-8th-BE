package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SlotValue {
    private Coordinate origin;
    private String region;
    private LocalDateTime datetime;
    private Integer availableTime;
    private List<String> category;

    public static SlotValue from(
            Coordinate coordinate,
            String requestedLocationName,
            LocalDateTime requestedDateTime,
            Integer availableTime,
            List<String> categories
    ) {
        return new SlotValue(
                coordinate,
                requestedLocationName,
                requestedDateTime,
                availableTime,
                categories
        );
    }
}
