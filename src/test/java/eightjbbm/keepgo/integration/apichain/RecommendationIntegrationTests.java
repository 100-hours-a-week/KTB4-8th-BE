package eightjbbm.keepgo.integration.apichain;

import eightjbbm.keepgo.auth.AccessTokenManager;
import eightjbbm.keepgo.auth.JwtProperties;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.recommendation.dto.RequestRecommendationRequest;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// 추천 도메인 통합 테스트
///
/// API 엔드포인트 총 7개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class RecommendationIntegrationTests {

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

    /// 외부 API 호출: AI 서버
    ///
    @Test
    @DisplayName("""
            여정 코스 추천 요청 API 통합 테스트
            """)
    void test1() {
        // AI 서버 응답은 Mocking해야 할 듯
        // 아마 별도의 중단 감지 로직을 추가로 구현해야 할 듯
        var result = webTestClient
                .post()
                .uri("/api/v1/user/recommendation")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    /// 외부 API 호출: AI 서버
    ///
    @Test
    @DisplayName("""
            여정 코스 추천 중단 API 통합 테스트
            """)
    void test2() {
        // AI 서버 응답은 Mocking해야 할 듯
        webTestClient
                .post()
                .uri("/api/v1/user/recommendation/cancellation")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("""
            요즘 뜨는 곳 목록 조회 API 통합 테스트 (더미 데이터 반환)
            """)
    void test3() {
        long cursor = 0L;
        long size = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/places?sort=trending&cursor=" + cursor + "&size=" + size)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("""
            광고 목록 조회 API 통합 테스트 (더미 데이터 반환)
            """)
    void test4() {
        var result = webTestClient
                .get()
                .uri("/api/v1/advertisements")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("""
            장소 상세 조회 API 통합 테스트 (미구현)
            """)
    void test5() {
        long placeId = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/places/" + placeId)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("""
            이벤트 상세 조회 API 통합 테스트 (미구현)
            """)
    void test6() {
        long eventId = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/events/" + eventId)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("""
            광고 상세 조회 API 통합 테스트 (미구현)
            """)
    void test7() {
        long advertisementId = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/advertisements/" + advertisementId)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }
}
