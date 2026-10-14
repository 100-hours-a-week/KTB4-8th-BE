package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.time.*;
import java.util.List;
import java.util.Map;

@Getter
@Embeddable
public class BusinessHours {
    private final String timezone;
    private final boolean estimated;
    @ElementCollection
    @CollectionTable(
            name = "weekly_hours"
    )
    private final Map<DayOfWeek, List<TimeInterval>> weeklyHours;
    @ElementCollection
    @CollectionTable(
            name = "exceptions"
    )
    private final Map<LocalDate, List<TimeInterval>> exceptions;

    public BusinessHours(
            String timezone,
            boolean estimated,
            Map<DayOfWeek, List<TimeInterval>> weeklyHours,
            Map<LocalDate, List<TimeInterval>> exceptions
    ) {
        this.timezone = timezone;
        this.estimated = estimated;
        this.weeklyHours = weeklyHours;
        this.exceptions = exceptions;
    }

    public boolean isAvailable(Instant datetime) {
        ZonedDateTime zonedDateTime = datetime.atZone(ZoneId.of(timezone));
        for (TimeInterval interval: exceptions.get(zonedDateTime.toLocalDate())) {
            if (interval.isBetween(zonedDateTime.toLocalTime())) return true;
        }

        for (TimeInterval interval: weeklyHours.get(zonedDateTime.getDayOfWeek())) {
            if (interval.isBetween(zonedDateTime.toLocalTime())) return true;
        }

        return false;
    }
}
