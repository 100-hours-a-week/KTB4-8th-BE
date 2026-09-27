package eightjbbm.keepgo.integration.refreshchain;

import eightjbbm.keepgo.auth.AccessTokenManager;
import eightjbbm.keepgo.auth.JwtProperties;
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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Refresh SecurityFilterChain 통합 테스트
///
/// API 엔드포인트 총 2개
@ActiveProfiles("test")
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class RefreshChainIntegrationTests {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    AccessTokenManager accessTokenManager;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    OAuthAccountRepository oAuthAccountRepository;

    private Member member;
    private OAuthAccount memberOAuthAccount;

    @BeforeEach
    void setUp() {
        member = Member.create("test");
        memberRepository.saveAndFlush(member);
        memberOAuthAccount = OAuthAccount.create(
                member,
                "test@test.com",
                "google",
                "0"
        );
        oAuthAccountRepository.saveAndFlush(memberOAuthAccount);
    }

    @AfterEach
    void tearDown() {
        oAuthAccountRepository.deleteById(memberOAuthAccount.getId());
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
        // RT 쿠키 달아야됨
        webTestClient
                .delete()
                .uri("/api/v1/user/auth-session")
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
        // RT 쿠키 달아야됨
        var result = webTestClient
                .post()
                .uri("/api/v1/user/auth-session/refresh")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
    }
}
