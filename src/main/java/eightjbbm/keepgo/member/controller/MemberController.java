package eightjbbm.keepgo.member.controller;

import eightjbbm.keepgo.member.MemberMapper;
import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/user")
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    /// 회원 정보 조회 API
    /// @param jwt
    /// @return {@link GetMemberInfoResponse}
    @GetMapping
    public ResponseEntity<GetMemberInfoResponse> getMemberInfo(@AuthenticationPrincipal Jwt jwt) {
        var command = memberMapper.toGetMemberInfoCommand(Long.valueOf(jwt.getSubject()));

        GetMemberInfoResult result = memberService.getMemberInfo(command);

        return ResponseEntity.status(HttpStatus.OK).body(GetMemberInfoResponse.from(result));
    }

    /// 회원 정보 수정 API
    /// @param jwt
    /// @param request {@link UpdateMemberInfoRequest}
    /// @return {@link UpdateMemberInfoResponse}
    @PatchMapping
    public ResponseEntity<UpdateMemberInfoResponse> updateMemberInfo(
            @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid UpdateMemberInfoRequest request) {
        var command = memberMapper.toUpdateMemberInfoCommand(Long.valueOf(jwt.getSubject()), request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(UpdateMemberInfoResponse.from(memberService.updateMemberInfo(command)));
    }
}
