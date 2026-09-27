package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/// Access Token 발급의 주체
///
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class AccessTokenManager {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final AtBlacklistCacheRepository atBlacklistCacheRepository;

    public String issue(
            Long memberId,
            Collection<String> roles
    ) {
        Instant now = Instant.now();
        var header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .type("at+jwt")
                .build();

        var claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(memberId.toString())
                .audience(List.of(jwtProperties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("client_id", jwtProperties.clientId())
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
