package eightjbbm.keepgo.integration.refreshchain;

import eightjbbm.keepgo.auth.AccessTokenManager;
import eightjbbm.keepgo.auth.JwtProperties;
import eightjbbm.keepgo.auth.RefreshTokenManager;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

/// Refresh SecurityFilterChain 통합 테스트
///
/// API 엔드포인트 총 2개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class RefreshChainIntegrationTests {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    AccessTokenManager accessTokenManager;

    @Autowired
    RefreshTokenManager refreshTokenManager;

    @Autowired
    MemberRepository memberRepository;

    private Member member;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        member = Member.create("test");
        memberRepository.saveAndFlush(member);
        refreshToken = refreshTokenManager.issue(member.getId());
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteById(member.getId());
    }

    private String issueAccessToken() {
        return accessTokenManager.issue(member.getId(), List.of());
    }

    @Test
    @DisplayName("""
            로그아웃 API 통합 테스트
            """)
    void test1() {
        webTestClient
                .delete()
                .uri("/api/v1/user/auth-session")
                .cookie("refresh_token", refreshToken)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("""
            Access Token 재발급 API 통합 테스트
            """)
    void test2() {
        IO.println("refresh token: " + refreshToken);
        var result = webTestClient
                .post()
                .uri("/api/v1/user/auth-session/refresh")
                .cookie("refresh_token", refreshToken)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isCreated()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }
}
