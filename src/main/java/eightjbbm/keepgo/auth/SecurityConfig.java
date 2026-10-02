package eightjbbm.keepgo.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistValidator;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;
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
    OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
            DataSource dataSource,
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        return new JdbcOAuth2AuthorizedClientService(
                new JdbcTemplate(dataSource),
                clientRegistrationRepository
        );
    }

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
    @Bean
    public SecurityFilterChain fallBackFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                EndpointRequest.to(HealthEndpoint.class)
                        ).permitAll()
                        // 관리 포트(8081)는 모니터링 보안 그룹에만 열린다.
                        .requestMatchers(
                                EndpointRequest.to(PrometheusScrapeEndpoint.class)
                        ).permitAll()
                        .requestMatchers("/public/**")
                        .permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .denyAll()
                )
                .build();
    }
}
