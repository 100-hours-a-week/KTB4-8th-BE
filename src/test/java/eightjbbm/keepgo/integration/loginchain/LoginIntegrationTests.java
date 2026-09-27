package eightjbbm.keepgo.integration.loginchain;

import eightjbbm.keepgo.auth.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/// 로그인 SecurityFilterChain 통합 테스트
///
/// API 엔드포인트 총 1개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoginIntegrationTests {

    @Autowired
    WebTestClient webTestClient;

    /// 외부 API 호출: 구글
    ///
    @Test
    @DisplayName("""
            구글 OAuth 로그인 API 통합 테스트
            """)
    void test1() {
        // 구글 로그인 자체를 Mocking해야 할 듯?
        var request = new LoginRequest("google");

        var result = webTestClient
                .post()
                .uri("/api/v1/user/auth-session")
                .bodyValue(request)
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
    }
}
