package eightjbbm.keepgo.util.cache.slot;

import eightjbbm.keepgo.util.Coordinate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
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
            Set<String> categories
    ) {
        return new SlotValue(
                query,
                coordinate,
                requestedLocationName,
                requestedDateTime,
                availableTime,
                categories
        );
    }

    public static SlotValue createEmptySlotValue() {
        return new SlotValue(
                null,
                new Coordinate(null, null),
                null,
                null,
                null,
                null
        );
    }

    public void addCategory(String category) {
        this.category.add(category);
    }

    public void updateDatetime(LocalDate date) {
        if (this.datetime == null) {
            this.datetime = date.atStartOfDay();
        } else {
            LocalTime time = this.datetime.toLocalTime();
            this.datetime = date.atTime(time);
        }
    }
}
