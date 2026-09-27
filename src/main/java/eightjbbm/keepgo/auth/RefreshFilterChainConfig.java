package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.exception.RefreshTokenFailAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class RefreshFilterChainConfig {

    @Order(10)
    @Bean
    public SecurityFilterChain rtFilterChain(
            HttpSecurity http,
            RefreshTokenFailAuthenticationEntryPoint entryPoint
    ) throws Exception {
        var authManager = http.getSharedObject(AuthenticationManager.class);
        return http
                .securityMatchers(
                        matchers -> matchers
                                .requestMatchers(
                                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/user-session/refresh"),
                                        PathPatternRequestMatcher.pathPattern(HttpMethod.DELETE, "/api/v1/auth/user-session")
                                )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
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
                        new RefreshFilter(authManager), AuthorizationFilter.class
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                )
                .build();
    }
}
