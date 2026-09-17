package eightjbbm.keepgo.member.controller;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/user")
    public GetMemberInfoResponse getMemberInfo(@AuthenticationPrincipal Jwt jwt) {
        GetMemberInfoCommand command = new GetMemberInfoCommand(
                Long.valueOf(jwt.getSubject())
        );

        return GetMemberInfoResponse.from(
                memberService.getMemberInfo(command)
        );
    }

    @PatchMapping("/user")
    public UpdateMemberInfoResponse updateMemberInfo(@AuthenticationPrincipal Jwt jwt, UpdateMemberInfoRequest request) {
        UpdateMemberInfoCommand command = new UpdateMemberInfoCommand(
                Long.valueOf(jwt.getSubject()),
                request.nickname(),
                request.profileImagePath()
        );

        return UpdateMemberInfoResponse.from(
                memberService.updateMemberInfo(command)
        );
    }

    @PostMapping
    public SynchronizeYoutubeLikeVideosResponse synchronizeYoutubeLikeVideos(@AuthenticationPrincipal Jwt jwt) {
        SynchronizeYoutubeLikeVideosCommand command = new SynchronizeYoutubeLikeVideosCommand(
                Long.valueOf(jwt.getSubject())
        );

        return SynchronizeYoutubeLikeVideosResponse.from(
                memberService.synchronizeYoutubeLikeVideos(command)
        );
    }
}
