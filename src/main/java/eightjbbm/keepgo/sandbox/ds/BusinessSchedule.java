package eightjbbm.keepgo.sandbox.ds;

import lombok.Getter;

import java.time.*;
import java.util.List;
import java.util.Map;

@Getter
public class BusinessSchedule {
    private final ZoneId timezone;
    private final boolean estimated;
    private final Map<DayOfWeek, List<TimeInterval>> weeklyHours;
    private final Map<LocalDate, List<TimeInterval>> exceptions;

    public BusinessSchedule(
            String timezone,
            boolean estimated,
            Map<DayOfWeek, List<TimeInterval>> weeklyHours,
            Map<LocalDate, List<TimeInterval>> exceptions
    ) {
        this.timezone = ZoneId.of(timezone);
        this.estimated = estimated;
        this.weeklyHours = weeklyHours;
        this.exceptions = exceptions;
    }

    public BusinessSchedule(
            ZoneId timezone,
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
        var localDateTime = datetime.atZone(timezone).toLocalDateTime();

        if (exceptions.containsKey(localDateTime.toLocalDate())) {
            for (TimeInterval timeInterval: exceptions.get(localDateTime.toLocalDate())) {
                if (timeInterval.isBetween(localDateTime.toLocalTime())) return true;
            }
        } else {
            for (TimeInterval timeInterval: weeklyHours.get(localDateTime.getDayOfWeek())) {
                if (timeInterval.isBetween(localDateTime.toLocalTime())) return true;
            }
        }

        return false;
    }
}
