package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

/// 여행 위시리스트에 추가한 행사 목록 조회 API의 컨트롤러 응답 DTO
///
public record GetEventListResponse(@Nullable String nextCursor, List<Event> events) {
    record Event(
            Long eventId,
            String name,
            String category,
            String description,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable Place place) {}

    record Place(String name, String addressSg) {}
}
