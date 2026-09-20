package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;

/// @param nickname 회원의 닉네임
/// @param profileImagePath 회원의 프로필 사진 주소
public record UpdateMemberInfoResult(
        String nickname,
        String profileImagePath
) {
    public static UpdateMemberInfoResult from(Member member) {
        return new UpdateMemberInfoResult(
                member.getNickname(),
                member.getProfileImage().getStoragePath()
        );
    }
}
