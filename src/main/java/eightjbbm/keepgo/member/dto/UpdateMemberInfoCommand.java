package eightjbbm.keepgo.member.dto;

public record UpdateMemberInfoCommand(
        Long userId,
        String nickname,
        String profileImagePath
) {
}
