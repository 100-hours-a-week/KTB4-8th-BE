package eightjbbm.keepgo.interestextraction.service;

import java.time.LocalDateTime;

/// 유튜브 연동 정보 조회 API 서비스 요청 DTO
///
public record GetYoutubeConnectionResult(
        String email,
        String status,
        LocalDateTime lastJobDatetime,
        int videosFetchedCount,
        int videosPendingCount
) {
}