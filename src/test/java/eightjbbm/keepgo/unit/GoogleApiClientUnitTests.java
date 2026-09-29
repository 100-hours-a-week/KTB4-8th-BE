package eightjbbm.keepgo.unit;

import eightjbbm.keepgo.util.client.google.GoogleApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GoogleApiClientUnitTests {

    @Autowired
    GoogleApiClient googleApiClient;

    @Test
    @DisplayName("""
            유튜브 좋아요 누른 동영상 재생목록 ID 조회 API 단위 테스트
            """)
    void test1() {

    }
}
