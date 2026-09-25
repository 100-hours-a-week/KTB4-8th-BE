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
    private Coordinate coordinate;
    private String requestedLocationName;
    private LocalDateTime requestedDateTime;
    private Integer availableTime;
    private List<String> categories;

    public static SlotValue from(
            Float lat,
            Float lng,
            String requestedLocationName,
            LocalDateTime requestedDateTime,
            Integer availableTime,
            List<String> categories
    ) {
        return new SlotValue(
                new Coordinate(lat, lng),
                requestedLocationName,
                requestedDateTime,
                availableTime,
                categories
        );
    }
}
