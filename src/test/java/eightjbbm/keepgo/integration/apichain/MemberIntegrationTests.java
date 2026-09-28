package eightjbbm.keepgo.integration.apichain;

import eightjbbm.keepgo.auth.AccessTokenManager;
import eightjbbm.keepgo.auth.JwtProperties;
import eightjbbm.keepgo.member.dto.UpdateMemberInfoRequest;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import eightjbbm.keepgo.util.file.File;
import eightjbbm.keepgo.util.file.FileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// 회원 도메인 통합 테스트
///
/// API 엔드포인트 총 3개
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({JwtProperties.class})
public class MemberIntegrationTests {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    AccessTokenManager accessTokenManager;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    FileRepository fileRepository;

    private Member member;
    private OAuthAccount memberOAuthAccount;
    private File newProfileImage;

    @BeforeEach
    void setUp() {
        member = Member.create("test");
        memberRepository.saveAndFlush(member);
        memberOAuthAccount = OAuthAccount.create(
                member,
                "test@test.com",
                "google",
                "0",
                "name"
        );
        oAuthAccountRepository.saveAndFlush(memberOAuthAccount);
        newProfileImage = new File("/public/profile/profile-20260928153150-b6d51dc9.png");
        fileRepository.saveAndFlush(newProfileImage);
    }

    @AfterEach
    void tearDown() {
        oAuthAccountRepository.deleteById(memberOAuthAccount.getId());
        memberRepository.deleteById(member.getId());
        fileRepository.deleteById(newProfileImage.getId());
    }

    private String issueAccessToken() {
        return accessTokenManager.issue(member.getId(), List.of());
    }

    @Test
    @DisplayName("""
            회원 정보 조회 API 통합 테스트
            """)
    void test1() throws Exception {
        var result = webTestClient
                .get()
                .uri("/api/v1/user")
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
            회원 정보 수정 API 통합 테스트
            """)
    void test2() throws Exception {
        IO.println("count: " + fileRepository.count());
        List<File> files = fileRepository.findAll();
        var request = new UpdateMemberInfoRequest(
                "new_nickname",
                newProfileImage.getStoragePath()
        );
        var result = webTestClient
                .patch()
                .uri("/api/v1/user")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .bodyValue(request)
                .exchange()
                .expectBody()
                .returnResult();

        IO.println("status = " + result.getStatus());
        IO.println("headers = " + result.getResponseHeaders());
        IO.println("body = " + new String(result.getResponseBodyContent(), StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    }
    /*

    /// 외부 API 호출: 구글
    ///
    @Test
    @DisplayName("""
            유튜브 동기화 API 통합 테스트
            """)
    void test3() throws Exception {


        member.setLikedVideosPlaylistId(String.valueOf(0));
        webTestClient
                .post()
                .uri("/api/v1/user/youtube-analyze")
                .headers(headers -> {
                    headers.setBearerAuth(issueAccessToken());
                })
                .exchange()
                .expectStatus().isNoContent();
    }

     */
}
