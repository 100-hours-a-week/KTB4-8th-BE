package eightjbbm.keepgo.member.controller;

import eightjbbm.keepgo.member.service.MemberService;
import eightjbbm.keepgo.member.dto.getUserInfoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/user/auth-session")
    public void login() {

    }

    @DeleteMapping("/user/auth-session")
    public void logout() {

    }

    @GetMapping("/user")
    public void getUserInfo() {
        Long memberId = 0L;
        getUserInfoResponseDto response = memberService.getUserInfo(memberId);
    }

    @PatchMapping("/user")
    public void updateUserInfo() {

    }

    @GetMapping("/user/accounts")
    public void getUserAccounts() {

    }
}
