package eightjbbm.keepgo.interestextraction.service;

/// 연결된 서비스 목록 조회 API 서비스 요청 DTO
///
public record GetConnectedServiceListCommand(
        Long memberId
) {
}
