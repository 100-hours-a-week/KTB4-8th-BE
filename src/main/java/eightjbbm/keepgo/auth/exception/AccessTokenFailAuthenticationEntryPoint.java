package eightjbbm.keepgo.auth.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AccessTokenFailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    private final String errorType = "/invalid-token";
    private final String errorTitle = "유효하지 않은 액세스 토큰입니다.";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        delegate.commence(request, response, authException);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        var body = ProblemDetail.forStatus(
                HttpStatus.UNAUTHORIZED
        );
        body.setType(URI.create(errorType));
        body.setTitle(errorTitle);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
