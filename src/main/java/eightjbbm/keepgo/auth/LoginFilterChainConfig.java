package eightjbbm.keepgo.auth;

import eightjbbm.keepgo.auth.authorizationrequest.OAuthFailureHandler;
import eightjbbm.keepgo.auth.authorizationrequest.SharedAuthorizationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@EnableWebSecurity
@Import(OidcLoginSuccessHandler.class)
@Configuration
@RequiredArgsConstructor
public class LoginFilterChainConfig {

    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private final SharedAuthorizationRequestRepository authorizationRequests;
    private final OAuthFailureHandler oauthFailureHandler;

    @Order(1)
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatchers(matchers -> matchers.requestMatchers(
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/user/auth-session"),
                        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/oauth2/**"),
                        PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/login/oauth2/code/**")))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/user/auth-session"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/oauth2/**"),
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/login/oauth2/code/**"))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(oauthFailureHandler, OAuth2AuthorizationRequestRedirectFilter.class)
                .oauth2Login(auth -> auth.withObjectPostProcessor(
                                new ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter>() {
                                    @Override
                                    public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(
                                            O filter) {
                                        filter.setAuthenticationFailureHandler(oauthFailureHandler);
                                        return filter;
                                    }
                                })
                        .authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestRepository(authorizationRequests))
                        .successHandler(oidcLoginSuccessHandler)
                        .failureHandler(oauthFailureHandler))
                .build();
    }
}
