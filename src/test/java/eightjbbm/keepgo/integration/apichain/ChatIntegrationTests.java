package eightjbbm.keepgo.integration.apichain;

import eightjbbm.keepgo.auth.AccessTokenManager;
import eightjbbm.keepgo.auth.JwtProperties;
import eightjbbm.keepgo.chat.dto.SendChatRequest;
import eightjbbm.keepgo.chat.dto.UpdateSlotRequest;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.geocoding.GeoCodingApiClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/// 채팅 도메인 통합 테스트
///
/// API 엔드포인트 총 5개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class ChatIntegrationTests {

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
            채팅 전송 API 통합 테스트
            """)
    void test1() {
        // AI 서버 응답은 Mocking 해야 할 듯.
        var request = new SendChatRequest("hello");

        var result = webTestClient
                .post()
                .uri("/api/v1/user/chat-messages")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }

    /// 외부 API 호출: 지오코딩
    ///
    @Test
    @DisplayName("""
            의도 카드 업데이트 API 통합 테스트
            """)
    void test2() {
        // AI 서버 응답은 Mocking해야 할 듯.
        var request = new UpdateSlotRequest(
                new Coordinate(126.978275264f, 37.566642192f),
                "test",
                LocalDateTime.now(),
                180,
                List.of("카페")
        );

        var result = webTestClient
                .patch()
                .uri("/api/v1/user/chat-messages/slot")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("""
            챗봇 응답 조회 API 통합 테스트 (응답 생성 진행 중)
            """)
    void test3() {
        long chatId = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/user/chat-messages/" + chatId + "/response")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("IN_PROGRESS")
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("""
            챗봇 응답 조회 API 통합 테스트 (응답 생성 완료)
            """)
    void test3_1() {
        long chatId = 0L;

        var result = webTestClient
                .get()
                .uri("/api/v1/user/chat-messages/" + chatId + "/response")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("""
            채팅 내역 조회 API 통합 테스트
            """)
    void test4() {
        // 커서랑 사이즈 조정 필요...
        long cursor = 0L;
        long size = 20L;
        var result = webTestClient
                .get()
                .uri("/api/v1/user/chat-messages?cursor=" + cursor + "&size=" + size)
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("""
            채팅 내역 초기화 API 통합 테스트
            """)
    void test5() {
        webTestClient
                .delete()
                .uri("/api/v1/user/chat-messages")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isNoContent();
    }
}
