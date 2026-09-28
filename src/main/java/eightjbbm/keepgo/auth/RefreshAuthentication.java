package eightjbbm.keepgo.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class RefreshAuthentication implements Authentication {
    @Getter
    private final String refreshToken;
    private final boolean authenticated;

    @Override
    public String getName() {
        return "";
    }

    @Override
    public @Nullable Object getPrincipal() {
        return null;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

    }

    @Override
    public @Nullable Object getDetails() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public @Nullable Object getCredentials() {
        return null;
    }

    public static RefreshAuthentication authenticationToken(String refreshToken) {
        return new RefreshAuthentication(refreshToken, false);
    }

    public static RefreshAuthentication unauthenticated(String refreshToken) {
        return new RefreshAuthentication(refreshToken, false);
    }

    public static RefreshAuthentication authenticated() {
        return new RefreshAuthentication(null, true);
    }
}
