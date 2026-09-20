package eightjbbm.keepgo.member.controller;

import eightjbbm.keepgo.member.dto.*;
import eightjbbm.keepgo.member.service.MemberService;
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

    /// 회원 정보 조회 API
    /// @param jwt
    /// @return {@link GetMemberInfoResponse}
    @GetMapping
    public ResponseEntity<GetMemberInfoResponse> getMemberInfo(@AuthenticationPrincipal Jwt jwt) {
        GetMemberInfoCommand command = new GetMemberInfoCommand(
                Long.valueOf(jwt.getSubject())
        );

        GetMemberInfoResult result = memberService.getMemberInfo(command);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        GetMemberInfoResponse.from(result)
                );
    }

    /// 회원 정보 수정 API
    /// @param jwt
    /// @param request {@link UpdateMemberInfoRequest}
    /// @return {@link UpdateMemberInfoResponse}
    @PatchMapping
    public ResponseEntity<UpdateMemberInfoResponse> updateMemberInfo(@AuthenticationPrincipal Jwt jwt, UpdateMemberInfoRequest request) {
        UpdateMemberInfoCommand command = new UpdateMemberInfoCommand(
                Long.valueOf(jwt.getSubject()),
                request.nickname(),
                request.profileImageUrl()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        UpdateMemberInfoResponse.from(
                                memberService.updateMemberInfo(command)
                        )
                );
    }

    /// 유튜브 동기화 API
    /// @param jwt
    @PostMapping("/youtube-analyze")
    public ResponseEntity<Void> synchronizeYoutubeLikeVideos(@AuthenticationPrincipal Jwt jwt) {
        SynchronizeYoutubeLikeVideosCommand command = new SynchronizeYoutubeLikeVideosCommand(
                Long.valueOf(jwt.getSubject())
        );

        memberService.synchronizeYoutubeLikeVideos(command);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
