package eightjbbm.keepgo.util.client.google;

import eightjbbm.keepgo.util.dto.RetrieveLikedVideosResponse;
import eightjbbm.keepgo.util.dto.RetrieveLikesPlaylistIdResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@RequiredArgsConstructor
public class GoogleApiClient {

    @Qualifier("googleRestClient")
    private final RestClient googleRestClient;

    public RetrieveLikesPlaylistIdResponse retrieveLikesPlaylistId(String googleAccessToken) {
        return googleRestClient.get()
                .uri(
                    uriBuilder -> {
                        UriBuilder builder = uriBuilder
                            .path("/youtube/v3/channels")
                            .queryParam("part", "contentDetails")
                            .queryParam("mine", "true");

                        return builder.build();
                    })
                .headers(headers -> {
                    headers.setBearerAuth(googleAccessToken);
                })
                .retrieve()
                .body(RetrieveLikesPlaylistIdResponse.class);
    }

    public GetLikedVideosResponse getLikedVideos(String googleAccessToken) {
        return googleRestClient.get()
                .uri(
                        uriBuilder -> {
                            var builder = uriBuilder
                                    .path("/youtube/v3/videos")
                                    .queryParam("part", "id")
                                    .queryParam("myRating", "like");

                            return builder.build();
                        }
                )
                .headers(headers -> headers
                        .setBearerAuth(googleAccessToken)
                )
                .retrieve()
                .body(GetLikedVideosResponse.class);
    }

    /// 첫 50개만 조회할 수 있음. 리팩토링 필요
    public RetrieveLikedVideosResponse retrieveLikedVideos(String likesPlaylistId, String googleAccessToken) {
        return googleRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/youtube/v3/playlistItems")
                                    .queryParam("part", "contentDetails")
                                    .queryParam("id", likesPlaylistId)
                                    .queryParam("maxResults", 50);

                            return builder.build();
                        }
                )
                .headers(headers -> headers.setBearerAuth(googleAccessToken))
                .retrieve()
                .body(RetrieveLikedVideosResponse.class);
    }
}
