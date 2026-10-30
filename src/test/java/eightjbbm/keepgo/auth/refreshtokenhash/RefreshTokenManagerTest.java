package eightjbbm.keepgo.auth.refreshtokenhash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eightjbbm.keepgo.auth.refreshtokenhash.repository.CaffeineRefreshTokenHashRepository;
import eightjbbm.keepgo.auth.refreshtokenhash.repository.RefreshTokenHashProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@DisplayName("리프레시 토큰 회전")
class RefreshTokenManagerTest {

    private final RefreshTokenHashProperties properties =
            new RefreshTokenHashProperties("refreshTokens", Duration.ofDays(14));
    private final RefreshTokenManager manager = new RefreshTokenManager(
            new CaffeineRefreshTokenHashRepository(properties, new CaffeineCacheManager(properties.name())),
            properties);

    @Test
    @DisplayName("같은 리프레시 토큰은 한 번만 회전할 수 있다")
    void aRefreshTokenCanOnlyBeRotatedOnce() {
        String token = manager.issue(1L);

        var rotation = manager.rotateWithMember(token);

        assertThat(rotation.memberId()).isEqualTo(1L);
        assertThat(rotation.refreshToken()).isNotEqualTo(token);
        assertThatThrownBy(() -> manager.rotateWithMember(token)).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(manager.get(token)).isEmpty();
    }

    @Test
    @DisplayName("회전 후 받은 새 토큰은 다시 사용할 수 있다")
    void theRotatedTokenRemainsUsable() {
        String token = manager.issue(1L);

        String rotated = manager.rotateWithMember(token).refreshToken();

        assertThat(manager.rotateWithMember(rotated).memberId()).isEqualTo(1L);
    }
}
