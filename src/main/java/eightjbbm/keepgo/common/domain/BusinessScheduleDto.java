package eightjbbm.keepgo.common.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BusinessScheduleDto(
        String timezone,
        Boolean estimated,
        Map<DayOfWeek, List<TimeIntervalDto>> weeklyHours,
        Map<LocalDate, List<TimeIntervalDto>> exceptions) {
    public record TimeIntervalDto(LocalTime openTime, LocalTime closeTime) {}

    public static BusinessScheduleDto from(BusinessSchedule businessSchedule) {
        return new BusinessScheduleDto(
                businessSchedule.getTimezone().toString(),
                businessSchedule.isEstimated(),
                businessSchedule.getWeeklyHours().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream()
                                        .map(timeInterval ->
                                                new TimeIntervalDto(timeInterval.startTime(), timeInterval.endTime()))
                                        .toList())),
                businessSchedule.getExceptions().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream()
                                        .map(timeInterval ->
                                                new TimeIntervalDto(timeInterval.startTime(), timeInterval.endTime()))
                                        .toList())));
    }
}
