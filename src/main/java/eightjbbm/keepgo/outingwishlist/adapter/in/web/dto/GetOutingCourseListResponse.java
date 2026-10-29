package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

/// 여행 위시리스트에 추가한 여행 코스 목록 조회 API 컨트롤러 응답 DTO
///
public record GetOutingCourseListResponse(@Nullable String nextCursor, List<Course> courses) {
    public record Course(
            Long courseId,
            String name,
            String addressSg,
            Integer durationHours,
            LocalDate saveDate,
            List<Stop> stops) {}

    public record Stop(Integer count, List<Item> items) {}

    public record Item(
            Integer sequence,
            Long stopId,
            String name,
            String type,
            String category,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate) {}
}
