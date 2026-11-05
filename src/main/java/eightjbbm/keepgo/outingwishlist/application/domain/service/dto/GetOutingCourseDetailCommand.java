package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

/// 여행 위시리스트에 추가한 여행 코스 상세 조회 API
///
public record GetOutingCourseDetailCommand(Long memberId, Long outingCourseId) {}
