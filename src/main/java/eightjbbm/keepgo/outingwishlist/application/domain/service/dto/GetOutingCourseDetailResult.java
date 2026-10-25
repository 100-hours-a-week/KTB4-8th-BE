package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntryType;
import eightjbbm.keepgo.recommendation.entity.OutingStop;
import eightjbbm.keepgo.util.Coordinate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/// 여행 위시리스트에 추가한 여행 코스 상세 조회 API 서비스 응답 DTO
///
public record GetOutingCourseDetailResult(
        Long courseId,
        String name,
        Integer durationHours,
        List<Stop> stops
) {
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
            Coordinate coordinate,
            Boolean isAvailable
    ) {}

    public static GetOutingCourseDetailResult from(
            OutingWishlistEntry entry,
            Instant referenceTime
    ) {
        var course = entry.getOutingCourse();

        var items = course.getStops().stream()
                .sorted(Comparator.comparingInt(
                        stop -> stop.getId().sequence()))
                .map(stop -> toItem(stop, referenceTime))
                .toList();

        return new GetOutingCourseDetailResult(
                entry.getId(),
                course.getName(),
                course.getDurationHours(),
                List.of(new Stop(items.size(), items)));
    }

    private static Item toItem(
            OutingStop stop,
            Instant referenceTime
    ) {
        var guide = stop.getOutingGuide();

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
                guide.getCoordinate(),
                guide.isAvailable(referenceTime));
    }
}
