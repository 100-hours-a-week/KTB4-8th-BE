package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

/// 여행 위시리스트에 추가한 장소 상세 조회 API 서비스 요청 DTO
///
public record GetPlaceDetailCommand(
        Long memberId,
        Long placeId
) {
}
