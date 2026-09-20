package eightjbbm.keepgo.util;

import eightjbbm.keepgo.util.dto.AnalyzeVideoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final String baseUrl = "";
    private final RestClient aiServerRestClient;

    public void recommendCourse() {

    }

    public void extractSlot() {

    }

    public AnalyzeVideoResponse analyzeVideo(String url) {
        return aiServerRestClient.post()
                .uri(
                        uriBuilder -> {
                            UriBuilder builder = uriBuilder
                                    .path(baseUrl);

                            return builder.build();
                        }
                )
                .body(url)
                .retrieve()
                .body(AnalyzeVideoResponse.class);
    }
}
