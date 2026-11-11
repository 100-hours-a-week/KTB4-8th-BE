package eightjbbm.keepgo.auth;

import java.util.ArrayList;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Google OAuth `google` 등록의 자격증명을 기동 시점에 검증한다.
///
/// 환경변수가 없으면 Boot는 `${GOOGLE_CLIENT_ID}`를 풀지 않고 리터럴 그대로 바인딩한다.
/// 그래서 빈 값과 미해석 플레이스홀더를 모두 누락으로 보고 어떤 환경변수가 없는지 알려 준다.
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.google")
public record GoogleOAuthCredentialsProperties(String clientId, String clientSecret) {

    public GoogleOAuthCredentialsProperties {
        var missing = new ArrayList<String>();
        if (isMissing(clientId)) {
            missing.add("GOOGLE_CLIENT_ID");
        }
        if (isMissing(clientSecret)) {
            missing.add("GOOGLE_CLIENT_SECRET");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Google OAuth 자격증명 환경변수가 설정되지 않았습니다: " + String.join(", ", missing));
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || value.startsWith("${");
    }
}
