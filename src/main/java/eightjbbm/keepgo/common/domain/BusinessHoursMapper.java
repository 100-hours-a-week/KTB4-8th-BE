package eightjbbm.keepgo.common.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class BusinessHoursMapper {
    public static BusinessSchedule toBusinessHours(BusinessScheduleDto dto) {
        if (dto == null) return null;

        return new BusinessSchedule(
                dto.timezone(),
                dto.estimated(),
                convertWeeklyHours(dto.weeklyHours()),
                convertExceptions(dto.exceptions()));
    }

    private static Map<DayOfWeek, List<TimeInterval>> convertWeeklyHours(
            Map<DayOfWeek, List<BusinessScheduleDto.TimeIntervalDto>> dto) {
        var result = new EnumMap<DayOfWeek, List<TimeInterval>>(DayOfWeek.class);
        if (dto == null) {
            return result;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            result.put(day, new ArrayList<>());
        }

        dto.forEach((dayOfWeek, intervals) -> {
            for (TimeInterval interval : convertIntervals(intervals)) {
                if (interval.crossesDateBoundary()) {
                    result.get(dayOfWeek).add(new TimeInterval(interval.startTime(), LocalTime.MAX));
                    result.get(dayOfWeek.plus(1)).add(new TimeInterval(LocalTime.MIN, interval.endTime()));
                } else {
                    result.get(dayOfWeek).add(interval);
                }
            }
        });

        return result;
    }

    private static Map<LocalDate, List<TimeInterval>> convertExceptions(
            Map<LocalDate, List<BusinessScheduleDto.TimeIntervalDto>> dto) {
        var result = new HashMap<LocalDate, List<TimeInterval>>();

        if (dto == null) {
            return result;
        }

        // 빈 리스트인 날짜도 보존한다.
        dto.keySet().forEach(date -> result.put(date, new ArrayList<>()));

        dto.forEach((date, intervals) -> {
            for (TimeInterval interval : convertIntervals(intervals)) {
                result.get(date)
                        .add(
                                interval.crossesDateBoundary()
                                        ? new TimeInterval(interval.startTime(), LocalTime.MAX)
                                        : interval);

                if (interval.crossesDateBoundary()) {
                    result.computeIfAbsent(date.plusDays(1), ignored -> new ArrayList<>())
                            .add(new TimeInterval(LocalTime.MIN, interval.endTime()));
                }
            }
        });

        return result;
    }

    private static List<TimeInterval> convertIntervals(List<BusinessScheduleDto.TimeIntervalDto> intervals) {
        if (intervals == null) {
            return List.of();
        }

        return intervals.stream()
                .map(interval -> new TimeInterval(interval.openTime(), interval.closeTime()))
                .toList();
    }
}
