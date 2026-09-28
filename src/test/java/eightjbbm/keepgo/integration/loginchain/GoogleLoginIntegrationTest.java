package eightjbbm.keepgo.integration.loginchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class GoogleLoginIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("""
            구글 로그인 테스트
            """)
    void test1() throws Exception {
        mockMvc.perform(
                get("/oauth2/authorization/google")
                        .with(oidcLogin()
                                .idToken(idToken -> idToken
                                        .claim(
                                                "sub",
                                                "google-subject-123"
                                        )
                                        .claim(
                                                "email",
                                                "test@gmail.com"
                                        )
                                        .claim(
                                                "name",
                                                "테스트 사용자"
                                        )
                                        .claim(
                                                "picture",
                                                "https://example.com/profile.png"
                                        )
                                )
                        )
        ).andExpect(status().isFound());
    }
}
