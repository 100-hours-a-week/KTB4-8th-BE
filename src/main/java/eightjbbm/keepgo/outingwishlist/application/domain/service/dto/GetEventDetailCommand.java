package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

/// 여행 위시리스트에 추가한 행사 상세 조회 API 서비스 요청 DTO
///
public record GetEventDetailCommand(Long memberId, Long eventId) {}
