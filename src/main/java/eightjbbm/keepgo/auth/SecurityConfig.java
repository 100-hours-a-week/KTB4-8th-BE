package eightjbbm.keepgo.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import eightjbbm.keepgo.auth.exception.AccessTokenFailAuthenticationEntryPoint;
import eightjbbm.keepgo.auth.exception.RefreshTokenFailAuthenticationEntryPoint;
import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistCacheService;
import eightjbbm.keepgo.util.cache.atblacklist.AtBlacklistValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RefreshFilter refreshFilter;
    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private static final String ISSUER = "https://auth.example.com";
    private static final String KEY_ID = "placeholder";

    @Bean
    RSAPublicKey accessTokenPublicKey(
            @Value(
                    "${security.jwt.public-key-location}"
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
                    "${security.jwt.private-key-location}"
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
                .build();

        var standardValidator = JwtValidators.createDefaultWithIssuer(ISSUER);
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
                .keyID(KEY_ID)
                .build();

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
        );

        return new NimbusJwtEncoder(jwkSource);
    }

    @Order(100)
    @Bean
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            AccessTokenFailAuthenticationEntryPoint entryPoint
    ) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/api/v1/users/me/profile-image",
                                "/public"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                        )
                        .authenticationEntryPoint(entryPoint)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                )
                .build();
    }

    @Order(10)
    @Bean
    public SecurityFilterChain rtFilterChain(
            HttpSecurity http,
            RefreshTokenFailAuthenticationEntryPoint entryPoint
    ) throws Exception {
        return http
                .securityMatchers(
                        matchers -> matchers
                                .requestMatchers(
                                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/user-session/refresh"),
                                        PathPatternRequestMatcher.pathPattern(HttpMethod.DELETE, "/api/v1/auth/user-session")
                        )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .authenticated()
                )
                .addFilterBefore(
                        refreshFilter, AuthorizationFilter.class
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                )
                .build();
    }

    @Order(1)
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http, AtBlacklistCacheService atBlacklistCacheService) throws Exception {
        return http
                .securityMatchers(
                        matchers -> matchers.requestMatchers(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/user-session"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/oauth/authorization/**")
                        )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(auth -> auth
                        .successHandler(oidcLoginSuccessHandler)
                )
                .build();
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
