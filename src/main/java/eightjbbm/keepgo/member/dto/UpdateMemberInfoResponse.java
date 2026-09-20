package eightjbbm.keepgo.member.dto;

/// @param nickname 회원의 닉네임
/// @param profileImageUrl 회원의 프로필 사진 주소
public record UpdateMemberInfoResponse(
        String nickname,
        String profileImageUrl
) {
    public static UpdateMemberInfoResponse from(UpdateMemberInfoResult result) {
        return null;
    };
}
