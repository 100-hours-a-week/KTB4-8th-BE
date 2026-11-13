package eightjbbm.keepgo.auth.authorizationrequest;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AuthorizationRequestCodec {
    private final ObjectMapper mapper;

    public String encode(OAuth2AuthorizationRequest request) {
        return mapper.writeValueAsString(new StoredRequest(
                1,
                request.getAuthorizationUri(),
                request.getClientId(),
                request.getRedirectUri(),
                request.getScopes(),
                request.getState(),
                request.getAdditionalParameters(),
                request.getAttributes(),
                request.getAuthorizationRequestUri()));
    }

    public OAuth2AuthorizationRequest decode(String json) {
        StoredRequest stored = mapper.readValue(json, StoredRequest.class);
        if (stored.version() != 1) {
            throw new IllegalStateException("Unsupported OAuth authorization request format");
        }
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(stored.authorizationUri())
                .clientId(stored.clientId())
                .redirectUri(stored.redirectUri())
                .scopes(stored.scopes())
                .state(stored.state())
                .additionalParameters(stored.additionalParameters())
                .attributes(stored.attributes())
                .authorizationRequestUri(stored.authorizationRequestUri())
                .build();
    }

    public record StoredRequest(
            int version,
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri) {}
}
