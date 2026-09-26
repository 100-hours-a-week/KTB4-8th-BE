package eightjbbm.keepgo.util.cache.atblacklist;

import eightjbbm.keepgo.auth.AccessTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    private final AccessTokenManager accessTokenManager;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (accessTokenManager.checkBlacklist(token.getId())) {
            return OAuth2TokenValidatorResult.success();
        } else {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN));
        }
    }
}
