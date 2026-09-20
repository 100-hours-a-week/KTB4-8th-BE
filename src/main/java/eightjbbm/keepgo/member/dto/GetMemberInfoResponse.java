package eightjbbm.keepgo.member.dto;

/**
 *
 * @param nickname 회원의 닉네임
 * @param email 회원의 이메일 주소
 * @param profileImageUrl 회원의 프로필 사진 주소
 */
public record GetMemberInfoResponse(
        String nickname,
        String email,
        String profileImageUrl
) {
    public static GetMemberInfoResponse from(GetMemberInfoResult result) {
        return new GetMemberInfoResponse(
                result.nickname(),
                result.email(),
                result.profileImageUrl());
    }
}
