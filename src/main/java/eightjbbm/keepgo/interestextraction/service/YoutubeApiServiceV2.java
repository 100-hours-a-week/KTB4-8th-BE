package eightjbbm.keepgo.interestextraction.service;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeApiServiceV2 {

    private final YoutubeAuthorizedClientService authorizedClientService;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public VideoListResponse getVideoLists(Long memberId, String pageToken) {
        OAuth2AccessToken accessToken = authorizedClientService.getAccessToken(memberId);
        var credentials = GoogleCredentials.create(
                new AccessToken(accessToken.getTokenValue(), Date.from(accessToken.getExpiresAt())));

        var youtube = new YouTube.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName("keepgo")
                .build();

        try {
            var builder = youtube.videos()
                    .list(List.of("id", "status", "snippet", "contentDetails"))
                    .setMaxResults(50L)
                    .setMyRating("like");

            if (pageToken != null) {
                builder = builder.setPageToken(pageToken);
            }

            long startNanos = System.nanoTime();
            try {
                return builder.execute();
            } finally {
                long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                log.info("YouTube videos.list 호출 소요 시간: {}ms", elapsedMillis);
            }
        } catch (Exception e) {
            log.error("YouTube videos.list 호출 실패", e);
            throw new ServiceUnavailableException();
        }
    }
}
