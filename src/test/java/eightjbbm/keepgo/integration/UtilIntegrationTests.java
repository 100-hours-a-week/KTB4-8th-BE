package eightjbbm.keepgo.integration;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.List;

/// 기타 도메인 통합 테스트
///
/// API 엔드포인트 총 1개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class UtilIntegrationTests {

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

    private String issueAccessToken() { return accessTokenManager.issue(member.getId(), List.of()); }

    @Test
    @DisplayName("""
            프로필 사진 업로드 API 통합 테스트
            """)
    void test1() {
        // 테스트용 임시 이미지 필요
        var image = new ClassPathResource("images/profile.png");
        var body = new MultipartBodyBuilder();
        body.part("image", image)
                .filename("profile.png")
                .contentType(MediaType.IMAGE_PNG);

        webTestClient
                .post()
                .uri("/api/v1/user/profile-image")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(
                        BodyInserters.fromMultipartData(
                                body.build()
                        )
                )
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION);
    }
}
