package eightjbbm.keepgo.member.dto;

import jakarta.validation.constraints.Pattern;

/// @param nickname 회원의 닉네임
/// @param profileImageUrl 회원의 프로필 사진 주소. 생략하면 기존 사진을 유지하며, 빈 문자열이나 공백뿐인 값은 거부한다.
public record UpdateMemberInfoRequest(
        @Pattern(regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]{1,10}$") String nickname,
        @Pattern(regexp = "(?U)(?s).*\\S.*") String profileImageUrl) {}
