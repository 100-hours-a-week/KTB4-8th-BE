package eightjbbm.keepgo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMemberInfoRequest(
        @Pattern(regexp = "^[a-zA-Z0-9]{1,10}$") String nickname,
        @NotBlank String profileImagePath
) {
}
