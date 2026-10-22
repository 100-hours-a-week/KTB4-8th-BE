package eightjbbm.keepgo.interestextraction.controller.v2;

import java.time.LocalDateTime;

/// 연결된 서비스 목록 조회 API 컨트롤러 응답 DTO
///
public record GetConnectedServiceListResponse(
        ServiceConnected serviceConnected
) {
    public record ServiceConnected(
            String name,
            LocalDateTime lastJobDatetime
    ) {}
}
