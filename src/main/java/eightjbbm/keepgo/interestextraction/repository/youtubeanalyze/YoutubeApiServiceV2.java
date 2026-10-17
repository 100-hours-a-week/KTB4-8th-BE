package eightjbbm.keepgo.interestextraction.repository.youtubeanalyze;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import eightjbbm.keepgo.interestextraction.service.YoutubeAuthorizedClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YoutubeApiServiceV2 {

    private final YoutubeAuthorizedClientService authorizedClientService;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public VideoListResponse getVideoLists(
            Long memberId,
            String pageToken
            ) throws Exception {
        OAuth2AccessToken accessToken = authorizedClientService.getAccessToken(memberId);
        var credentials = GoogleCredentials.create(
                new AccessToken(
                        accessToken.getTokenValue(),
                        Date.from(accessToken.getExpiresAt())
                )
        );

        var youtube = new YouTube.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName("keepgo")
                .build();

        var builder = youtube
                .videos()
                .list(List.of("id", "status", "snippet", "contentDetails"))
                .setMyRating("like");

        if (pageToken != null) {
            builder = builder.setPageToken(pageToken);
        }

        return builder.execute();
    }
}
