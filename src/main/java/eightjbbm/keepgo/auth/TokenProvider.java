package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.entity.OAuthAccount;
import eightjbbm.keepgo.member.repository.OAuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final String baseUrl = "http://www.example.com";
    private final OAuthAccountRepository oAuthAccountRepository;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    public Optional<Jwt> validateAndExtractAccessToken(String token) {
        if (accessTokenBlacklistRepository.existsByAccessToken(token)) {
            return Optional.empty();
        }
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return Optional.of(jwt);
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    public void validateRefreshToken(Member member, String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException(""); //RT 유효성 검증 실패 401
        }
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        if (sha256(token).equals(oAuthAccount.getRefreshTokenEncrypted())) {
            issueAndRotateRefreshToken(member);
        } else {
            throw new RuntimeException(""); //RT 유효성 검증 실패 401
        }
    }

    public String issueAccessToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(baseUrl)
                .expiresAt(now.plusSeconds(3600))
                .audience(List.of(baseUrl))
                .subject(member.getId().toString())
                .issuedAt(now)
                .id(UUID.randomUUID().toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String issueAndRotateRefreshToken(Member member) {
        String refreshToken = generateRefreshToken();
        String refreshTokenHash = sha256(refreshToken);
        OAuthAccount oAuthAccount = oAuthAccountRepository.findByMember(member).orElseThrow();
        oAuthAccount.rotateRefreshToken(refreshTokenHash);
        return refreshToken;
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 Unsupported", e);
        }
    }
}
