package eightjbbm.keepgo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;

@Component
@RequiredArgsConstructor
public class YoutubeApiClient {

    private final RestClient youtubeRestClient;

    public String retrieveLikesPlaylistId(Long userId) {
        return youtubeRestClient.get()
                .uri(
                    uriBuilder -> {
                        UriBuilder builder = uriBuilder
                            .path("/channels")
                            .queryParam("part", "contentDetails")
                            .queryParam("mine", "true");

                        return builder.build();
                    })
                .retrieve()
                .body();
    }

    public List<String> retrieveLikedVideos(String likesPlaylistId) {
        return youtubeRestClient.get()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path("/playlistItems")
                                    .queryParam("part", "snippet")
                                    .queryParam("id", likesPlaylistId);
                        }
                )
                .retrieve()
                .body();
    }
}
