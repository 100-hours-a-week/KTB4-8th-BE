package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import eightjbbm.keepgo.util.Coordinate;
import java.util.List;

/// 여행 위시리스트에 추가한 여행 코스 상세 조회 API 컨트롤러 응답 DTO
///
public record GetOutingCourseDetailResponse(Long courseId, String name, Integer durationMinutes, Stop stops) {
    public record Stop(Integer count, List<Item> items) {}

    public record Item(
            Integer sequence,
            Long stopId,
            String name,
            String type,
            String category,
            Integer durationMinutes,
            Coordinate coordinate,
            Boolean isAvailable) {}
}
