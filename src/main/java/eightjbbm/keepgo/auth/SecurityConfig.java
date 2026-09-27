package eightjbbm.keepgo.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtProperties jwtProperties;

    @Bean
    RSAPublicKey accessTokenPublicKey(
            @Value(
                    "${keepgo.security.jwt.public-key}"
            )
            Resource publicKeyResource
    ) throws IOException {
        try (InputStream inputStream = publicKeyResource.getInputStream()) {
            return RsaKeyConverters
                    .x509()
                    .convert(inputStream);
        }
    }

    @Bean
    RSAPrivateKey accessTokenPrivateKey(
            @Value(
                    "${keepgo.security.jwt.private-key}"
            )
            Resource privateKeyResource
    ) throws IOException {
        try (InputStream inputStream = privateKeyResource.getInputStream()) {
            return RsaKeyConverters
                    .pkcs8()
                    .convert(inputStream);
        }
    }

    @Bean
    JwtDecoder jwtDecoder(
            RSAPublicKey publicKey,
            AtBlacklistValidator blacklistValidator
    ) {
        var decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(
                        SignatureAlgorithm.RS256
                )
                .validateType(false)
                .build();

        var standardValidator = JwtValidators.createAtJwtValidator()
                .issuer(jwtProperties.issuer())
                .audience(jwtProperties.audience())
                .clientId(jwtProperties.clientId())
                .build();
        var validators = new DelegatingOAuth2TokenValidator<>(
                standardValidator,
                blacklistValidator
        );

        decoder.setJwtValidator(validators);

        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey
    ) {
        var rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(jwtProperties.keyId())
                .build();

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
        );

        return new NimbusJwtEncoder(jwkSource);
    }


    @Order(1000)
    public SecurityFilterChain fallBackFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .denyAll()
                )
                .build();
    }
}
