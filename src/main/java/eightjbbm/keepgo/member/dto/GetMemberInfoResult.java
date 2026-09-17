package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;

public record GetMemberInfoResult(
        String nickname,
        String profileImagePath,
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
