package eightjbbm.keepgo.member.dto;

import jakarta.validation.constraints.Pattern;

/// @param nickname 회원의 닉네임
/// @param profileImageUrl 회원의 프로필 사진 주소. 생략하면 기존 사진을 유지한다.
public record UpdateMemberInfoRequest(
        @Pattern(regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]{1,10}$") String nickname,
        @Pattern(regexp = "(?s).*\\S.*") String profileImageUrl) {}
