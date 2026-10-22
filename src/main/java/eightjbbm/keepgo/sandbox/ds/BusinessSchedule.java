package eightjbbm.keepgo.sandbox.ds;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public BusinessSchedule(
            @JsonProperty("timezone") String timezone,
            @JsonProperty("estimated") boolean estimated,
            @JsonProperty("weeklyHours") Map<DayOfWeek, List<TimeInterval>> weeklyHours,
            @JsonProperty("exceptions") Map<LocalDate, List<TimeInterval>> exceptions
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
        var local = datetime.atZone(timezone);
        var date = local.toLocalDate();

        List<TimeInterval> intervals = exceptions.containsKey(date)
                ? exceptions.getOrDefault(date, List.of())
                : weeklyHours.getOrDefault(
                local.getDayOfWeek(),
                List.of()
        );

        return intervals.stream()
                .anyMatch(interval -> interval.isBetween(local.toLocalTime()));
    }
}
