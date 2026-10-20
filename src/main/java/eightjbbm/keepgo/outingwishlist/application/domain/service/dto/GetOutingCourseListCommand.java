package eightjbbm.keepgo.outingwishlist.application.domain.service.dto;

import jakarta.annotation.Nullable;

/// 여행 위시리스트에 추가한 여행 코스 목록 조회 API 서비스 요청 DTO
///
public record GetOutingCourseListCommand(
        Long memberId,
        @Nullable String location,
        @Nullable String cursor
) {
}
