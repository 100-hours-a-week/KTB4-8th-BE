package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;

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
