package eightjbbm.keepgo.member.dto;

/// @param memberId 회원 ID
/// @param nickname 회원의 닉네임
/// @param profileImagePath 회원의 프로필 사진 주소
public record UpdateMemberInfoCommand(
        Long memberId,
        String nickname,
        String profileImagePath
) {
}
