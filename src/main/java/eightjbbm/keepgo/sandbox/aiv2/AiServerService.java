package eightjbbm.keepgo.sandbox.aiv2;

import eightjbbm.keepgo.recommendation.entity.OutingGuide;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
public class AiServerService {

    private final RestClient restClient;

    public AiServerService(@Qualifier("aiServerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AnalyzeVideoResponseV2 analyzeVideoV2(List<String> urls) {
        var request = restClient.post()
                .uri("/v1/analyze-video")
                .body(convert(urls));

        long startNanos = System.nanoTime();
        try {
            return request.exchange((_, res) -> {
                int status = res.getStatusCode().value();
                return switch (status) {
                    case 200 -> res.bodyTo(AnalyzeVideoResponseV2.Success.class);
                    case 400, 422, 429, 500, 502, 504 ->
                            res.bodyTo(AnalyzeVideoResponseV2.Failure.class);
                    default -> throw new RuntimeException();
                };
            });
        } finally {
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(System.nanoTime() - startNanos);
            log.info("AI /v1/analyze-video 호출 소요 시간: {}ms", elapsedMillis);
        }
    }

    private AnalyzeVideoRequestV2 convert(List<String> urls) {
        var videoUrls = urls.stream()
                .map(k -> {
                    return "https://youtube.com/shorts/" + k;
                })
                .toList();
        return new AnalyzeVideoRequestV2(videoUrls);
    }

    public void embedPlaces(List<OutingGuide> newOutingGuides) {
        var request = restClient.post()
                .uri("/v1/embed-places")
                .body(EmbedPlaceRequestV2.from(newOutingGuides));

        long startNanos = System.nanoTime();
        try {
            request.retrieve()
                    .toBodilessEntity();
        } finally {
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(System.nanoTime() - startNanos);
            log.info("AI /v1/embed-places 호출 소요 시간: {}ms", elapsedMillis);
        }
    }
}
