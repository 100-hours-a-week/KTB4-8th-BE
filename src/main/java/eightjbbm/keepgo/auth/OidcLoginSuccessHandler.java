package eightjbbm.keepgo.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        //1. Authentication에서 OidcUser 추출

        //2. OidcUser 내부 정보를 이용해 클라이언트 - 회원 간 매핑 테이블 조회하여 회원 검색
        //2-1. 결과가 존재할 시 기존 회원
        //2-2. 결과가 미존재할 시 신규 회원, 회원가입 진행

        //3. 회원 정보를 이용하여 AT/RT 생성하여 반환(AT는 헤더에, RT는 쿠키에)
    }
}
