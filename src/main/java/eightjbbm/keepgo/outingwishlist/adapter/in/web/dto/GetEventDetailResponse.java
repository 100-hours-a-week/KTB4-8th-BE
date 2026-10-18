package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import eightjbbm.keepgo.util.Coordinate;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

/// 여행 위시리스트에 추가한 행사 상세 조회 API 컨트롤러 응답 DTO
///
public record GetEventDetailResponse(
        Long eventId,
        String name,
        String category,
        String description,
        List<String> videosFeaturedLink,
        LocalDate saveDate,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable Place place
) {
    record Place(
            String name,
            String addressSg,
            String addressRoad,
            Coordinate coordinate
    ) {}
}
