package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/// Access Token 발급의 주체
///
@Service
@RequiredArgsConstructor
public class AccessTokenManager {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private final JwtEncoder jwtEncoder;
    private final String KEY_ID = "placeholder";
    private final String ISSUER = "https://auth.example.com";
    private final AtBlacklistCacheRepository atBlacklistCacheRepository;

    public String issue(
            Long memberId,
            Collection<String> roles
    ) {
        Instant now = Instant.now();
        var header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(KEY_ID)
                .type("at+jwt")
                .build();

        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(memberId.toString())
                .audience(List.of())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();

        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims
                )
        );

        return jwt.getTokenValue();
    }

    public boolean checkBlacklist(String jti) {
        return atBlacklistCacheRepository.read(jti).isPresent();
    }

    public void revoke(String jti) {
        atBlacklistCacheRepository.create(jti);
    }
}
