package eightjbbm.keepgo.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@EnableWebSecurity
@Import(OidcLoginSuccessHandler.class)
@Configuration
@RequiredArgsConstructor
public class LoginFilterChainConfig {

    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;

    @Order(1)
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatchers(
                        matchers -> matchers.requestMatchers(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/user-session"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/oauth/authorization/**")
                        )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(auth -> auth
                        .successHandler(oidcLoginSuccessHandler)
                )
                .build();
    }
}
