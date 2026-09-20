package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;

/**
 *
 * @param nickname 회원의 닉네임
 * @param profileImageUrl 회원의 프로필 사진 주소
 * @param email 회원의 이메일 주소
 */
public record GetMemberInfoResult(
        String nickname,
        String profileImageUrl,
        String email
) {
    public static GetMemberInfoResult from(Member member) {
        return new GetMemberInfoResult(
                member.getNickname(),
                member.getProfileImage().getStoragePath(),
                member.getNickname()
        );
    }
}
