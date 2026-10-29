package eightjbbm.keepgo.interestextraction.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class YoutubeAuthorizedClientConfig {

    @Bean("youtubeAuthorizedClientManager")
    public OAuth2AuthorizedClientManager youtubeAuthorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);

        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build());

        return manager;
    }
}
