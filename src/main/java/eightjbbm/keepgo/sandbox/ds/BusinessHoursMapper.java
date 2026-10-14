package eightjbbm.keepgo.sandbox.ds;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class BusinessHoursMapper {
    public static BusinessHours toBusinessHours(BusinessHoursDto dto) {
        if (dto == null) return null;

        return new BusinessHours(
                dto.timezone(),
                dto.estimated(),
                convertWeeklyHours(dto.weeklyHours()),
                convertExceptions(dto.exceptions())
        );
    }

    private static Map<DayOfWeek, List<TimeInterval>> convertWeeklyHours(
            Map<DayOfWeek, List<BusinessHoursDto.TimeIntervalDto>> dto
    ) {
        var result = new EnumMap<DayOfWeek, List<TimeInterval>>(DayOfWeek.class);
        if (dto == null) {
            return result;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            result.put(day, new ArrayList<>());
        }

        dto.forEach((dayOfWeek, intervals) ->
                {
                    for (TimeInterval interval: convertIntervals(intervals)) {
                        if (interval.crossesDateBoundary()) {
                            result.get(dayOfWeek).add(new TimeInterval(interval.startTime(), LocalTime.MAX));
                            result.get(dayOfWeek.plus(1)).add(new TimeInterval(LocalTime.MIN, interval.endTime()));
                        } else {
                            result.get(dayOfWeek).add(interval);
                        }
                    }
                }
        );

        return result;
    }

    private static Map<LocalDate, List<TimeInterval>> convertExceptions(
            Map<LocalDate, List<BusinessHoursDto.TimeIntervalDto>> dto
    ) {
        var result = new HashMap<LocalDate, List<TimeInterval>>();

        if (dto == null) {
            return result;
        }

        dto.forEach((date, intervals) ->
                {
                    for (TimeInterval interval: convertIntervals(intervals)) {
                        if (interval.crossesDateBoundary()) {
                            result.get(date).add(new TimeInterval(interval.startTime(), LocalTime.MAX));
                            result.computeIfAbsent(
                                    date.plusDays(1),
                                    _ -> new ArrayList<>()
                            ).add(
                                    new TimeInterval(LocalTime.MIN, interval.endTime())
                            );
                        } else {
                            result.get(date).add(interval);
                        }
                    }
                }
        );

        return result;
    }

    private static List<TimeInterval> convertIntervals(List<BusinessHoursDto.TimeIntervalDto> intervals) {
        return intervals.stream().map(k -> new TimeInterval(k.openTime(), k.closeTime())).toList();
    }
}
