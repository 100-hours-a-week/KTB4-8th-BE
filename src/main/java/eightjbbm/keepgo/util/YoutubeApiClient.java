package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class YoutubeApiClient {

    private final RestClient youtubeRestClient;
    private final String baseUrl = "https://www.googleapis.com";

    public RetrieveLikesPlaylistIdResponse retrieveLikesPlaylistId(Long userId) {
        return youtubeRestClient.get()
                .uri(
                    uriBuilder -> {
                        UriBuilder builder = uriBuilder
                            .path("/youtube/v3/channels")
                            .queryParam("part", "contentDetails")
                            .queryParam("mine", "true");

                        return builder.build();
                    })
                .retrieve()
                .body(RetrieveLikesPlaylistIdResponse.class);
    }

    /// 첫 50개만 조회할 수 있음. 리팩토링 필요
    public RetrieveLikedVideosResponse retrieveLikedVideos(String likesPlaylistId) {
        return youtubeRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .host(baseUrl)
                                    .path("/youtube/v3/playlistItems")
                                    .queryParam("part", "contentDetails")
                                    .queryParam("id", likesPlaylistId)
                                    .queryParam("maxResults", 50);

                            return builder.build();
                        }
                )
                .retrieve()
                .body(RetrieveLikedVideosResponse.class);
    }
}
