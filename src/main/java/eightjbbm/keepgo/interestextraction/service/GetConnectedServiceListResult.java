package eightjbbm.keepgo.interestextraction.service;

import java.time.LocalDateTime;

/// 유튜브 연동 정보 조회 API 서비스 응답 DTO
///
public record GetConnectedServiceListResult(
        ServiceConnected serviceConnected
) {
    public record ServiceConnected(
        String name,
        LocalDateTime lastJobDatetime
    ) {}
}
