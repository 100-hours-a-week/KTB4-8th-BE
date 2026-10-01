package eightjbbm.keepgo.member.dto;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.util.file.File;

import java.util.Optional;

/// @param nickname 회원의 닉네임
/// @param profileImagePath 회원의 프로필 사진 주소
public record UpdateMemberInfoResult(
        String nickname,
        String profileImagePath
) {
    public static UpdateMemberInfoResult from(Member member) {
        String storagePath = Optional.ofNullable(member.getProfileImage()).map(File::getStoragePath).orElse("");
        return new UpdateMemberInfoResult(
                member.getNickname(),
                storagePath
        );
    }
}
