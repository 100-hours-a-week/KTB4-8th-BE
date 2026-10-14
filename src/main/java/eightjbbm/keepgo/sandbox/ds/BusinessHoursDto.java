package eightjbbm.keepgo.sandbox.ds;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record BusinessHoursDto(
        String timezone,
        Boolean estimated,
        Map<DayOfWeek, List<TimeIntervalDto>> weeklyHours,
        Map<LocalDate, List<TimeIntervalDto>> exceptions
) {
    public record TimeIntervalDto(
            LocalTime openTime,
            LocalTime closeTime
    ) {}
}
