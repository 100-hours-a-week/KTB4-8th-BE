package eightjbbm.keepgo.auth.refreshtokenhash;

import eightjbbm.keepgo.auth.exception.RefreshTokenFailAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class RefreshFilterChainConfig {

    @Order(10)
    @Bean
    public SecurityFilterChain rtFilterChain(
            HttpSecurity http,
            RefreshTokenFailAuthenticationEntryPoint entryPoint,
            RefreshAuthenticationProvider provider)
            throws Exception {
        var authManager = new ProviderManager(provider);
        var refreshFilter = createRefreshFilter(entryPoint, authManager);
        return http.securityMatchers(matchers -> matchers.requestMatchers(
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/user/auth-session/refresh")))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(AbstractHttpConfigurer::disable)
                .authenticationProvider(provider)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(refreshFilter, AuthorizationFilter.class)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
                .build();
    }

    private static @NonNull RefreshFilter createRefreshFilter(
            RefreshTokenFailAuthenticationEntryPoint entryPoint, ProviderManager authManager) {
        var refreshFilter = new RefreshFilter(authManager);
        refreshFilter.setSuccessHandler(new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request, HttpServletResponse response, Authentication authentication)
                    throws IOException, ServletException {}

            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain,
                    Authentication authentication)
                    throws IOException, ServletException {
                chain.doFilter(request, response);
            }
        });
        refreshFilter.setFailureHandler(new AuthenticationEntryPointFailureHandler(entryPoint));
        return refreshFilter;
    }
}
