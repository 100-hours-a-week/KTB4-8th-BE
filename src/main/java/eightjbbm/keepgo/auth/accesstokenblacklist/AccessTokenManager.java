package eightjbbm.keepgo.auth.accesstokenblacklist;

import eightjbbm.keepgo.auth.JwtProperties;
import eightjbbm.keepgo.auth.accesstokenblacklist.repository.AccessTokenBlacklisted;
import eightjbbm.keepgo.auth.accesstokenblacklist.repository.AccessTokenBlacklistedProperties;
import eightjbbm.keepgo.auth.accesstokenblacklist.repository.AccessTokenBlacklistedRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/// Access Token 발급의 주체
///
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, AccessTokenBlacklistedProperties.class})
public class AccessTokenManager {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final AccessTokenBlacklistedProperties accessTokenBlacklistedProperties;
    private final AccessTokenBlacklistedRepository accessTokenBlacklistedRepository;

    public String issue(Long memberId, Collection<String> roles) {
        Instant now = Instant.now();
        var header = JwsHeader.with(SignatureAlgorithm.RS256)
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

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return jwt.getTokenValue();
    }

    public boolean checkBlacklist(String jti) {
        return accessTokenBlacklistedRepository.existsById(jti);
    }

    public void revoke(String jti) {
        accessTokenBlacklistedRepository.save(
                new AccessTokenBlacklisted(jti, Instant.now().plus(accessTokenBlacklistedProperties.ttl())));
    }
}
