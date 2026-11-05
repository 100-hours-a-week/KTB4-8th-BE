package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import java.util.List;

/// 여행 위시리스트에 추가한 장소 목록 조회 API 컨트롤러 응답 DTO
///
public record GetPlaceListResponse(String nextCursor, List<Place> places) {
    record Place(Long placeId, String name, String category, String description, String addressSg) {}
}
