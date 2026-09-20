package eightjbbm.keepgo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/// @param nickname 회원의 닉네임
/// @param profileImageUrl 회원의 프로필 사진 주소
public record UpdateMemberInfoRequest(
        @Pattern(regexp = "^[a-zA-Z0-9]{1,10}$") String nickname,
        @NotBlank String profileImageUrl
) {
}
