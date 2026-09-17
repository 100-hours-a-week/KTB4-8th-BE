package eightjbbm.keepgo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        //1. 요청의 Authorization 헤더에서 AT 추출

        //2. AT 유효성 검사
        //2-1. 성공 시: Authentication 생성 후 SecurityContext에 저장
        //2-2. 실패 시: 401 - AT 유효성 검증 실패

        filterChain.doFilter(request, response);
    }
}
