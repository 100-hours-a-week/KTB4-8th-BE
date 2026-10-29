package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Deprecated
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SlotValue {
    private String query;
    private Coordinate origin;
    private String region;
    private LocalDateTime datetime;
    private Integer availableTime;
    private Set<String> category;

    public static SlotValue from(
            String query,
            Coordinate coordinate,
            String requestedLocationName,
            LocalDateTime requestedDateTime,
            Integer availableTime,
            Set<String> categories) {
        return new SlotValue(query, coordinate, requestedLocationName, requestedDateTime, availableTime, categories);
    }
}
