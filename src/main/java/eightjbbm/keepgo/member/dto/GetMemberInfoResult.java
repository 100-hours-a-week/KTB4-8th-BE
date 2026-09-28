package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;

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
    public static GetMemberInfoResult from(Member member, OAuthAccount oAuthAccount) {
        String profileImageUrl = null;
        if (member.getProfileImage() != null) {
            profileImageUrl = member.getProfileImage().getStoragePath();
        }
        return new GetMemberInfoResult(
                member.getNickname(),
                profileImageUrl,
                oAuthAccount.getEmail()
        );
    }
}
