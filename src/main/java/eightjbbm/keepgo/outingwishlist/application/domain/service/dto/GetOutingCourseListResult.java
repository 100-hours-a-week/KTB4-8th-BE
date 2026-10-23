package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingCollectionPrivate;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.recommendation.entity.OutingEvent;
import eightjbbm.keepgo.recommendation.entity.OutingStop;
import jakarta.annotation.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// 여행 위시리스트에 추가한 여행 코스 목록 조회 API 서비스 응답 DTO
///
public record GetOutingCourseListResult(
        @Nullable String nextCursor,
        List<Course> courses
) {
    public record Course(
            Long courseId,
            String name,
            String addressSg,
            Integer durationHours,
            LocalDate saveDate,
            List<Stop> stops
    ) {}

    public record Stop(
            Integer count,
            List<Item> items
    ) {}

    public record Item(
            Integer sequence,
            Long stopId,
            String name,
            String type,
            String category,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate
    ) {}

    public static GetOutingCourseListResult from(
            List<OutingCollectionPrivate> entries,
            List<OutingStop> stops,
            String nextCursor
    ) {
        var stopsByCourseId = stops.stream()
                .collect(Collectors.groupingBy(
                        stop -> stop.getId().outingCourseId()));

        var zone = ZoneId.systemDefault();

        var courses = entries.stream()
                .map(entry -> toCourse(entry, stopsByCourseId, zone))
                .toList();

        return new GetOutingCourseListResult(nextCursor, courses);
    }

    private static Course toCourse(
            OutingCollectionPrivate entry,
            Map<Long, List<OutingStop>> stopsByCourseId,
            ZoneId zone
    ) {
        var course = entry.getOutingCourse();

        var items = stopsByCourseId
                .getOrDefault(course.getId(), List.of())
                .stream()
                .sorted(Comparator.comparingInt(
                        stop -> stop.getId().sequence()))
                .map(stop -> toItem(stop, zone))
                .toList();

        return new Course(
                entry.getId(),
                course.getName(),
                course.getLocation(),
                course.getDurationHours(),
                entry.getCreateDatetime().atZone(zone).toLocalDate(),
                List.of(new Stop(items.size(), items)));
    }

    private static Item toItem(OutingStop stop, ZoneId zone) {
        var guide = stop.getOutingGuide();

        LocalDate startDate = null;
        LocalDate endDate = null;

        if (guide instanceof OutingEvent event) {
            if (event.getStartDatetime() != null) {
                startDate = event.getStartDatetime()
                        .atZone(zone)
                        .toLocalDate();
            }

            if (event.getEndDatetime() != null) {
                endDate = event.getEndDatetime()
                        .atZone(zone)
                        .toLocalDate();
            }
        }

        String type = switch (OutingWishlistEntryType.getType(guide)) {
            case PLACE -> "place";
            case EVENT -> "event";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 코스 구성 요소입니다.");
        };

        return new Item(
                stop.getId().sequence(),
                guide.getId(),
                guide.getName(),
                type,
                guide.getCategory(),
                startDate,
                endDate);
    }
}
