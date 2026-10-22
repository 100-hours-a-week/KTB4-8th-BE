package eightjbbm.keepgo.interestextraction.controller.v2;

import java.time.LocalDateTime;

/// 유튜브 연동 정보 조회 API 컨트롤러 응답 DTO
///
public record GetYoutubeConnectionResponse(
        String email,
        String status,
        LocalDateTime lastJobDatetime,
        int videosFetchedCount,
        int videosPendingCount
) {
}
