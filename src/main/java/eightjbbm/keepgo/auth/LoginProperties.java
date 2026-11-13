package eightjbbm.keepgo.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keepgo.security")
public record LoginProperties(String redirectionAddress) {

    public LoginProperties {
        if (redirectionAddress != null && redirectionAddress.contains("#")) {
            throw new IllegalArgumentException("redirectionAddress에는 Fragment(#)를 쓸 수 없습니다");
        }
    }
}
