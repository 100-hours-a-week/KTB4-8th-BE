package eightjbbm.keepgo.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("로그인 설정")
class LoginPropertiesTest {

    @Test
    @DisplayName("Fragment가 없는 리다이렉트 주소는 허용한다")
    void acceptsAddressWithoutFragment() {
        assertThatCode(() -> new LoginProperties("http://localhost:3000/login/callback?from=home"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fragment가 있는 리다이렉트 주소는 거부한다")
    void rejectsAddressWithFragment() {
        assertThatThrownBy(() -> new LoginProperties("https://app.example/#/login/callback"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
