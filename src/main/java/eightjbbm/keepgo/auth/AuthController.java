package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.dto.LoginRequest;
import eightjbbm.keepgo.auth.dto.LoginResponse;
import eightjbbm.keepgo.auth.dto.LogoutResponse;
import eightjbbm.keepgo.auth.dto.RefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    @PostMapping("/user/auth-session")
    public LoginResponse login(LoginRequest request) {
        //기본 OAuth2Login 페이지로 매핑만 해보자.
        return LoginResponse.from();
    }

    @DeleteMapping("/user/auth-session")
    public LogoutResponse logout(@AuthenticationPrincipal Jwt jwt) {
        //1. 요청에서 추출한 AT 무효화
        //2. 요청의 쿠키에서 RT 추출 후 무효화?
        //3. RT 검증 테이블에서 해당 RT의 정보 무효화
        return LogoutResponse.from();
    }

    @PostMapping("/user/auth-session/refresh")
    public RefreshResponse refresh() {
        //1. 요청의 쿠키에서 RT 추출
        //2. RT 유효성 검사
        //2-1. 실패 시 401: RT 유효성 검증 실패
        //3. 성공 시 AT/RT 재발급, 원본 요청 반환
        return RefreshResponse.from();
    }
}
