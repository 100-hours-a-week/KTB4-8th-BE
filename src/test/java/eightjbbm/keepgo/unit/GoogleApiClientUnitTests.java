package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.config.TestGoogleClientConfig;
import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import eightjbbm.keepgo.util.client.google.GoogleApiClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;

/// 유튜브 API 요청 단위 테스트
///
/// 구글 OAuth Playground에서 테스트용 토큰 발급 후 사용 가능
@ActiveProfiles("test")
@SpringJUnitConfig({
        GoogleApiClientConfig.class,
        TestGoogleClientConfig.class
})
public class GoogleApiClientUnitTests {

    /*
    @Autowired
    GoogleApiClient googleApiClient;

    @Test
    @DisplayName("""
            유튜브 좋아요 누른 동영상 목록 조회 API 단위 테스트
            """)
    void test1() {
        String googleAccessToken = "";
        var response = googleApiClient.getLikedVideos(googleAccessToken);
        IO.println(response);
    }

     */
}
