package eightjbbm.keepgo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientId;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@TestConfiguration
@EnableConfigurationProperties(TestOAuth2Properties.class)
public class TestOAuth2Config {

    @Bean
    OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
            TestOAuth2Properties properties
    ) {
        String principalName = "test";
        String tokenValue = "test-token-value";
        Instant now = Instant.now();

        var clientRegistration = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .scope(properties.scope())
                .build();

        return new InMemoryOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(
                        clientRegistration
                ),
                Map.of(
                        new OAuth2AuthorizedClientId(
                                clientRegistration.getRegistrationId(),
                                principalName
                        ),
                        new OAuth2AuthorizedClient(
                                clientRegistration,
                                principalName,
                                new OAuth2AccessToken(
                                        OAuth2AccessToken.TokenType.BEARER,
                                        tokenValue,
                                        now,
                                        now.plusSeconds(3600),
                                        clientRegistration.getScopes()
                                )
                        )
                )
        );
    }
}
