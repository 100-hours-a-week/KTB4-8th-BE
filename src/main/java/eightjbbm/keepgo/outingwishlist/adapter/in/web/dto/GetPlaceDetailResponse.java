package eightjbbm.keepgo.outingwishlist.adapter.in.web.dto;

import eightjbbm.keepgo.util.Coordinate;
import java.time.LocalDate;
import java.util.List;

/// 여행 위시리스트에 추가한 장소 상세 조회 API 컨트롤러 응답 DTO
///
public record GetPlaceDetailResponse(
        Long placeId,
        String name,
        String category,
        String description,
        List<String> videosFeaturedLink,
        LocalDate saveDate,
        String addressSg,
        String addressRoad,
        Coordinate coordinate) {}
